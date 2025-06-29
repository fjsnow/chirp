package io.fjsn.chirp;

import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.internal.handler.EventDispatcher;
import io.fjsn.chirp.internal.redis.JedisSubscriber;
import io.fjsn.chirp.internal.serialization.PacketSerializer;
import io.fjsn.chirp.internal.util.ChirpLogger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    private Thread mainSubscriberThread;
    private Thread serviceSubscriberThread;

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

    Chirp(String channel, String origin, ChirpRegistry registry, JedisPool jedisPool) {
        this.channel = CHANNEL_PREFIX + channel;
        this.origin = origin;
        this.registry = registry;
        this.jedisPool = jedisPool;
        this.eventDispatcher = new EventDispatcher(registry);
    }

    public String getChannel() {
        return channel;
    }

    public String getOrigin() {
        return origin;
    }

    public ChirpRegistry getRegistry() {
        return registry;
    }

    public void connect(String redisHost, int redisPort) {
        connect(redisHost, redisPort, null);
    }

    public void connect(String redisHost, int redisPort, String redisPassword) {
        long startTime = System.currentTimeMillis();
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
            long endTime = System.currentTimeMillis();
            ChirpLogger.severe(
                    "Error connecting to Redis in "
                            + (endTime - startTime)
                            + "ms: "
                            + e.getMessage());
            throw new RuntimeException("Error connecting to Redis: " + e.getMessage(), e);
        }
        long endTime = System.currentTimeMillis();
        ChirpLogger.info("Connected to Redis in " + (endTime - startTime) + "ms.");
    }

    public void cleanup() {
        if (jedisPool != null) {
            jedisPool.close();
            jedisPool = null;
        }

        if (mainSubscriberThread != null && mainSubscriberThread.isAlive()) {
            mainSubscriberThread.interrupt();
            ChirpLogger.info("Main subscriber thread interrupted.");
        }

        if (serviceSubscriberThread != null && serviceSubscriberThread.isAlive()) {
            serviceSubscriberThread.interrupt();
            ChirpLogger.info("Service subscriber thread interrupted.");
        }

        registry.cleanup();
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

    private Thread startSubscriberThread(String channel, String threadName) {
        Thread thread =
                new Thread(
                        () -> {
                            while (!Thread.currentThread().isInterrupted()) {
                                try (Jedis jedis = jedisPool.getResource()) {
                                    JedisSubscriber subscriber =
                                            new JedisSubscriber(this, registry, eventDispatcher);
                                    ChirpLogger.info(
                                            "Attempting to subscribe to channel: " + channel);
                                    jedis.subscribe(subscriber, channel);
                                } catch (JedisConnectionException e) {
                                    ChirpLogger.warning(
                                            "Redis connection lost or refused for subscriber on"
                                                    + " channel '"
                                                    + channel
                                                    + "'. Retrying in 5 seconds...");
                                    try {
                                        TimeUnit.SECONDS.sleep(5);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        ChirpLogger.info(
                                                "Subscriber reconnection thread interrupted for"
                                                        + " channel "
                                                        + channel
                                                        + ".");
                                        break;
                                    }
                                } catch (Exception e) {
                                    ChirpLogger.severe(
                                            "Unexpected error in Redis subscriber for channel '"
                                                    + channel
                                                    + "': "
                                                    + e.getMessage());
                                    e.printStackTrace();
                                    try {
                                        TimeUnit.SECONDS.sleep(5);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                            }
                        },
                        threadName);

        thread.start();
        return thread;
    }

    public void subscribe() {
        long startTime = System.nanoTime();
        if (jedisPool == null) {
            throw new IllegalStateException("JedisPool not initialized. Call connect() first.");
        }

        mainSubscriberThread = startSubscriberThread(channel, "Chirp-Subscriber-Main");
        serviceSubscriberThread =
                startSubscriberThread(channel + ":" + origin, "Chirp-Subscriber-Service");

        long endTime = System.nanoTime();
        ChirpLogger.info(
                "Subscribed to channels: "
                        + channel
                        + " and "
                        + channel
                        + ":"
                        + origin
                        + " in "
                        + (endTime - startTime) / 1_000_000.0
                        + "ms.");
    }

    public void publish(Object packet) {
        publish(packet, null, false, null);
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

    private <T> void publishPacket(
            Object packet,
            String finalChannel,
            boolean isResponse,
            UUID respondingTo,
            boolean self,
            ChirpCallback<T> callback) {
        long startTime = System.nanoTime();

        if (jedisPool == null) {
            throw new IllegalStateException("JedisPool not initialized. Call setup() first.");
        }
        if (packet == null) {
            throw new IllegalArgumentException("Packet cannot be null");
        }

        String type =
                packet.getClass()
                        .getSimpleName()
                        .replaceAll("([a-z])([A-Z])", "$1_$2")
                        .toUpperCase();

        if (!registry.getPacketRegistry().containsKey(type)) {
            long endTime = System.nanoTime();
            String action = isResponse ? "respond" : "publish";
            ChirpLogger.severe(
                    "Failed to "
                            + action
                            + " (type not registered) in "
                            + (endTime - startTime) / 1_000_000.0
                            + "ms: Packet type "
                            + type
                            + " is not registered");
            throw new IllegalArgumentException("Packet type " + type + " is not registered");
        }

        UUID packetId = UUID.randomUUID();

        if (callback != null) {
            registry.registerCallback(packetId, callback);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String serializedJson =
                    PacketSerializer.toJsonString(
                            packet,
                            packetId,
                            origin,
                            isResponse,
                            respondingTo,
                            self,
                            System.currentTimeMillis(),
                            registry);
            jedis.publish(finalChannel, serializedJson);

            long endTime = System.nanoTime();
            String actionLog = isResponse ? "response" : "packet";
            ChirpLogger.debug(
                    "Published "
                            + actionLog
                            + " to channel: "
                            + finalChannel
                            + " in "
                            + (endTime - startTime) / 1_000_000.0
                            + "ms.");
            ChirpLogger.debug(
                    "Raw packet: "
                            + PacketSerializer.toPrettyJsonString(
                                    packet,
                                    packetId,
                                    origin,
                                    isResponse,
                                    respondingTo,
                                    self,
                                    System.currentTimeMillis(),
                                    registry));
        } catch (Exception e) {
            long endTime = System.nanoTime();
            String actionLog = isResponse ? "respond to event" : "publish packet";
            ChirpLogger.severe(
                    "Failed to "
                            + actionLog
                            + " in "
                            + (endTime - startTime) / 1_000_000.0
                            + "ms: "
                            + e.getMessage());
        }
    }

    public <T> void publish(
            Object packet, String destination, boolean self, ChirpCallback<T> callback) {
        String finalChannel = destination == null ? channel : channel + ":" + destination;
        publishPacket(packet, finalChannel, false, null, self, callback);
    }

    public void respond(ChirpPacketEvent<?> event, Object response, boolean self) {
        String finalChannel = channel + ":" + event.getOrigin();
        publishPacket(response, finalChannel, true, event.getPacketId(), self, null);
    }

    private static String generateRandomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        while (sb.length() < length) {
            sb.append(Integer.toHexString((int) (Math.random() * 16)));
        }
        return sb.toString();
    }
}
