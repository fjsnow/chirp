package io.fjsn.chirp;

import io.fjsn.chirp.annotation.ChirpPacket;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.internal.EventDispatcher;
import io.fjsn.chirp.internal.JedisSubscriber;
import io.fjsn.chirp.internal.PacketSerializer;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class Chirp {

    private final String channel;
    private final String origin;
    private boolean debug;

    private JedisPool jedisPool;
    private final ChirpRegistry registry;
    private final EventDispatcher eventDispatcher;

    public Chirp(String channel) {
        this(channel, generateRandomHex(16));
    }

    public Chirp(String channel, String origin) {
        this.channel = "chirp:" + channel;
        this.origin = origin;
        this.registry = new ChirpRegistry();
        this.registry.registerDefaultConverters();
        this.eventDispatcher = new EventDispatcher(registry);
    }

    public String getChannel() {
        return channel;
    }

    public String getOrigin() {
        return origin;
    }

    public boolean isDebug() {
        return debug;
    }

    public Chirp setDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    public Chirp connect(String redisHost, int redisPort) {
        connect(redisHost, redisPort, null);
        return this;
    }

    public Chirp connect(String redisHost, int redisPort, String redisPassword) {
        JedisPoolConfig redisConfig = new JedisPoolConfig();
        if (redisPassword == null || redisPassword.isEmpty()) {
            this.jedisPool = new JedisPool(redisConfig, redisHost, redisPort, 2000);
        } else {
            this.jedisPool = new JedisPool(redisConfig, redisHost, redisPort, 2000, redisPassword);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String response = jedis.ping();
            if ("PONG".equals(response)) {
                System.out.println("[Chirp] Connected to Redis");
            } else {
                throw new RuntimeException(
                        "[Chirp] Failed to connect to Redis: Unexpected response " + response);
            }
        } catch (Exception e) {
            throw new RuntimeException("[Chirp] Error connecting to Redis: " + e.getMessage(), e);
        }

        return this;
    }

    public void cleanup() {
        if (jedisPool != null) {
            jedisPool.close();
            jedisPool = null;
        }
        registry.cleanup();
    }

    public Chirp scan(String packageName) {
        registry.scan(packageName);
        return this;
    }

    public Chirp registerPacket(Class<?> packetClass) {
        registry.registerPacket(packetClass);
        return this;
    }

    public Chirp registerConverter(Class<?> genericType, FieldConverter<?> converter) {
        registry.registerConverter(genericType, converter);
        return this;
    }

    public Chirp addListener(Class<?> listenerClass) {
        registry.addListener(listenerClass);
        return this;
    }

    public Chirp debug() {
        this.debug = true;
        return this;
    }

    public Chirp subscribe() {
        if (jedisPool == null) {
            throw new IllegalStateException("JedisPool not initialized. Call setup() first.");
        }

        new Thread(
                        () -> {
                            try (Jedis jedis = jedisPool.getResource()) {
                                JedisSubscriber subscriber =
                                        new JedisSubscriber(
                                                registry, eventDispatcher::dispatchEvent);

                                jedis.subscribe(subscriber, channel);
                            } catch (Exception e) {
                                System.err.println(
                                        "[Chirp] Error subscribing to channel: " + e.getMessage());
                            }
                        })
                .start();

        System.out.println("[Chirp] Subscribed to channel: " + channel);
        return this;
    }

    public void publish(Object packet) {
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
                PacketSerializer.toJsonString(packet, origin, System.currentTimeMillis(), registry);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel, serializedJson);
        } catch (Exception e) {
            System.err.println("[Chirp] Error publishing packet: " + e.getMessage());
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
