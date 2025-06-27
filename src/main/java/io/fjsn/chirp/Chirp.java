package io.fjsn.chirp;

import io.fjsn.chirp.annotation.ChirpPacket;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.internal.ChirpLogger;
import io.fjsn.chirp.internal.EventDispatcher;
import io.fjsn.chirp.internal.JedisSubscriber;
import io.fjsn.chirp.internal.PacketSerializer;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.UUID;

public class Chirp {

    public static ChirpBuilder builder() {
        return new ChirpBuilder();
    }

    private static final String CHANNEL_PREFIX = "chirp:";

    private final String channel;
    private final String origin;

    private JedisPool jedisPool;
    private final ChirpRegistry registry;
    private final EventDispatcher eventDispatcher;

    public Chirp(String channel) {
        this(channel, generateRandomHex(16));
    }

    public Chirp(String channel, String origin) {
        this.channel = CHANNEL_PREFIX + channel;
        this.origin = origin;
        this.registry = new ChirpRegistry();
        this.registry.registerDefaultConverters();
        this.eventDispatcher = new EventDispatcher(registry);
        ChirpLogger.debug(
                "Chirp initialized with channel: " + this.channel + " and origin: " + this.origin);
    }

    public String getChannel() {
        return channel;
    }

    public String getOrigin() {
        return origin;
    }

    public void connect(String redisHost, int redisPort) {
        connect(redisHost, redisPort, null);
    }

    public void connect(String redisHost, int redisPort, String redisPassword) {
        JedisPoolConfig redisConfig = new JedisPoolConfig();
        if (redisPassword == null || redisPassword.isEmpty()) {
            this.jedisPool = new JedisPool(redisConfig, redisHost, redisPort, 2000);
        } else {
            this.jedisPool = new JedisPool(redisConfig, redisHost, redisPort, 2000, redisPassword);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String response = jedis.ping();
            if ("PONG".equals(response)) {
                ChirpLogger.info("Connected to Redis");
            } else {
                throw new RuntimeException(
                        "Failed to connect to Redis: Unexpected response " + response);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error connecting to Redis: " + e.getMessage(), e);
        }
    }

    public void cleanup() {
        if (jedisPool != null) {
            jedisPool.close();
            jedisPool = null;
        }
        registry.cleanup();
    }

    public void scan(String packageName) {
        registry.scan(packageName);
    }

    public void registerPacket(Class<?> packetClass) {
        registry.registerPacket(packetClass);
    }

    public void registerListener(Object listenerObject) {
        registry.registerListener(listenerObject);
    }

    public void registerConverter(Class<?> genericType, FieldConverter<?> converter) {
        registry.registerConverter(genericType, converter);
    }

    public void setupCallbackRemoverThread() {
        registry.setupCallbackRemoverThread();
    }

    public void subscribe() {
        if (jedisPool == null) {
            throw new IllegalStateException("JedisPool not initialized. Call setup() first.");
        }

        new Thread(
                        () -> {
                            try (Jedis jedis = jedisPool.getResource()) {
                                JedisSubscriber subscriber =
                                        new JedisSubscriber(this, registry, eventDispatcher);
                                jedis.subscribe(subscriber, channel);
                            } catch (Exception e) {
                                throw new RuntimeException(
                                        "Error subscribing to main channel: " + e.getMessage());
                            }
                        })
                .start();

        new Thread(
                        () -> {
                            try (Jedis jedis = jedisPool.getResource()) {
                                JedisSubscriber subscriber =
                                        new JedisSubscriber(this, registry, eventDispatcher);
                                jedis.subscribe(subscriber, channel + ":" + origin);
                            } catch (Exception e) {
                                throw new RuntimeException(
                                        "Error subscribing to service channel: " + e.getMessage());
                            }
                        })
                .start();

        ChirpLogger.info("Subscribed to channels: " + channel + " and " + channel + ":" + origin);
    }

    public void publish(Object packet) {
        publish(packet, channel, false, null);
    }

    public void publish(Object packet, String destination) {
        publish(packet, destination, false, null);
    }

    public void publish(Object packet, boolean self) {
        publish(packet, null, self, null);
    }

    public <T> void publish(Object packet, ChirpCallback<T> callback) {
        publish(packet, null, false, callback);
    }

    public void publish(Object packet, String destination, boolean self) {
        publish(packet, destination, false, null);
    }

    public <T> void publish(Object packet, String destination, ChirpCallback<T> callback) {
        publish(packet, destination, false, callback);
    }

    public <T> void publish(Object packet, boolean self, ChirpCallback<T> callback) {
        publish(packet, null, self, callback);
    }

    public <T> void publish(
            Object packet, String destination, boolean self, ChirpCallback<T> callback) {
        if (jedisPool == null) {
            throw new IllegalStateException("JedisPool not initialized. Call setup() first.");
        }
        if (packet == null) {
            throw new IllegalArgumentException("Packet cannot be null");
        }
        if (!packet.getClass().isAnnotationPresent(ChirpPacket.class)) {
            throw new IllegalArgumentException("Packet must be annotated with @ChirpPacket");
        }

        UUID packetId = UUID.randomUUID();
        String serializedJson =
                PacketSerializer.toJsonString(
                        packet,
                        packetId,
                        origin,
                        false,
                        null,
                        self,
                        System.currentTimeMillis(),
                        registry);

        if (callback != null) registry.registerCallback(packetId, callback);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(
                    destination == null ? channel : channel + ":" + destination, serializedJson);
            ChirpLogger.debug(
                    "Published packet to channel: "
                            + (destination == null ? channel : destination));
            ChirpLogger.debug(
                    "Raw packet: "
                            + PacketSerializer.toPrettyJsonString(
                                    packet,
                                    packetId,
                                    origin,
                                    false,
                                    null,
                                    self,
                                    System.currentTimeMillis(),
                                    registry));
        } catch (Exception e) {
            ChirpLogger.severe("Failed to publish packet: " + e.getMessage());
        }
    }

    public void respond(ChirpPacketEvent<?> event, Object response, boolean self) {
        if (jedisPool == null) {
            throw new IllegalStateException("JedisPool not initialized. Call setup() first.");
        }
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }
        if (!response.getClass().isAnnotationPresent(ChirpPacket.class)) {
            throw new IllegalArgumentException("Response must be annotated with @ChirpPacket");
        }

        UUID packetId = UUID.randomUUID();
        String serializedJson =
                PacketSerializer.toJsonString(
                        response,
                        packetId,
                        origin,
                        true,
                        event.getPacketId(),
                        self,
                        System.currentTimeMillis(),
                        registry);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel + ":" + event.getOrigin(), serializedJson);
            ChirpLogger.debug("Published packet to channel: " + channel + ":" + event.getOrigin());
            ChirpLogger.debug(
                    "Raw packet: "
                            + PacketSerializer.toPrettyJsonString(
                                    response,
                                    packetId,
                                    origin,
                                    true,
                                    event.getPacketId(),
                                    self,
                                    System.currentTimeMillis(),
                                    registry));
        } catch (Exception e) {
            ChirpLogger.severe("Failed to respond to event: " + e.getMessage());
        }
    }

    private static String generateRandomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        while (sb.length() < length) {
            sb.append(Integer.toHexString((int) (Math.random() * 16)));
        }
        return sb.toString();
    }
}
