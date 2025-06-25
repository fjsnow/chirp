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

    private static final String CHANNEL = "chirp";

    private final String origin;
    private JedisPool jedisPool;

    private final ChirpRegistry registry;
    private final EventDispatcher eventDispatcher;

    public Chirp() {
        this(generateRandomHex(16));
    }

    public Chirp(String origin) {
        this.origin = origin;
        this.registry = new ChirpRegistry();
        this.registry.registerDefaultConverters();

        this.eventDispatcher = new EventDispatcher(registry);
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

        System.out.println("[Chirp] Connected to Redis");
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

    public void registerConverter(Class<?> genericType, FieldConverter<?> converter) {
        registry.registerConverter(genericType, converter);
    }

    public void addListener(Class<?> listenerClass) {
        registry.addListener(listenerClass);
    }

    public void publish(Object packet) {
        publish(packet, false);
    }

    public void publish(Object packet, boolean toSelf) {
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
            jedis.publish(CHANNEL, serializedJson);
            System.out.println("[Chirp] Published packet: " + packet.getClass().getSimpleName());
        } catch (Exception e) {
            System.err.println("[Chirp] Error publishing packet: " + e.getMessage());
        }
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
                                                registry, eventDispatcher::dispatchEvent);

                                jedis.subscribe(subscriber, CHANNEL);
                            } catch (Exception e) {
                                System.err.println(
                                        "[Chirp] Error subscribing to channel: " + e.getMessage());
                            }
                        })
                .start();
    }

    private static String generateRandomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        while (sb.length() < length) {
            sb.append(Integer.toHexString((int) (Math.random() * 16)));
        }
        return sb.toString();
    }
}
