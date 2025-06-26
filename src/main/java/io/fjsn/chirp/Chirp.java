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

    public void subscribe() {
        if (jedisPool == null) {
            throw new IllegalStateException("JedisPool not initialized. Call setup() first.");
        }

        new Thread(
                        () -> {
                            try (Jedis jedis = jedisPool.getResource()) {
                                JedisSubscriber subscriber =
                                        new JedisSubscriber(
                                                this, registry, eventDispatcher::dispatchEvent);

                                jedis.subscribe(subscriber, channel);
                            } catch (Exception e) {
                                throw new RuntimeException(
                                        "Error subscribing to channel: " + e.getMessage());
                            }
                        })
                .start();

        ChirpLogger.info("Subscribed to channel: " + channel);
    }

    public void publish(Object packet) {
        publish(packet, false);
    }

    public void publish(Object packet, boolean self) {
        if (jedisPool == null) {
            throw new IllegalStateException("JedisPool not initialized. Call setup() first.");
        }
        if (packet == null) {
            throw new IllegalArgumentException("Packet cannot be null");
        }
        if (!packet.getClass().isAnnotationPresent(ChirpPacket.class)) {
            throw new IllegalArgumentException("Packet must be annotated with @ChirpPacket");
        }

        String serializedJson =
                PacketSerializer.toJsonString(
                        packet, origin, self, System.currentTimeMillis(), registry);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel, serializedJson);
        } catch (Exception e) {
            ChirpLogger.severe("Failed to publish packet: " + e.getMessage());
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
