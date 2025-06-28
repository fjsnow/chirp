package io.fjsn.chirp;

import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.internal.ChirpLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ChirpBuilder {

    private String channel;
    private String origin;
    private String scanPackageName;

    private Map<Class<?>, FieldConverter<?>> converters;
    private List<Class<?>> packetClasses;
    private ArrayList<Object> listenerObjects;

    private String redisUsername;
    private int redisPort;
    private String redisPassword;

    public ChirpBuilder() {
        packetClasses = new ArrayList<>();
        listenerObjects = new ArrayList<>();
        converters = new HashMap<>();
    }

    public ChirpBuilder channel(String channel) {
        this.channel = channel;
        return this;
    }

    public ChirpBuilder origin(String origin) {
        this.origin = origin;
        return this;
    }

    public ChirpBuilder scan(String packageName) {
        this.scanPackageName = packageName;
        return this;
    }

    public ChirpBuilder converter(Class<?> genericType, FieldConverter<?> converter) {
        converters.put(genericType, converter);
        return this;
    }

    public ChirpBuilder packet(Class<?> packetClass) {
        packetClasses.add(packetClass);
        return this;
    }

    public ChirpBuilder listener(Object listenerObject) {
        listenerObjects.add(listenerObject);
        return this;
    }

    public ChirpBuilder redis(String username, int port) {
        return redis(username, port, null);
    }

    public ChirpBuilder redis(String username, int port, String password) {
        this.redisUsername = username;
        this.redisPort = port;
        this.redisPassword = password;
        return this;
    }

    public ChirpBuilder debug(boolean debug) {
        ChirpLogger.debug = debug;
        return this;
    }

    public Chirp build() {
        long startTime = System.currentTimeMillis();
        if (channel == null || channel.isEmpty()) {
            throw new RuntimeException("Channel must be set");
        }

        Chirp chirp = origin != null ? new Chirp(channel, origin) : new Chirp(channel);

        if (scanPackageName != null) {
            long scanStart = System.nanoTime();
            chirp.scan(scanPackageName);
            long scanEnd = System.nanoTime();
            ChirpLogger.debug(
                    "ChirpBuilder: Scan phase completed in "
                            + (scanEnd - scanStart) / 1_000_000.0
                            + "ms.");
        }

        long manualRegisterStart = System.nanoTime();
        for (Class<?> clazz : packetClasses) chirp.registerPacket(clazz);
        for (Object obj : listenerObjects) chirp.registerListener(obj);
        for (Entry<Class<?>, FieldConverter<?>> entry : converters.entrySet())
            chirp.registerConverter(entry.getKey(), entry.getValue());
        long manualRegisterEnd = System.nanoTime();
        ChirpLogger.debug(
                "ChirpBuilder: Manual registrations completed in "
                        + (manualRegisterEnd - manualRegisterStart) / 1_000_000.0
                        + "ms.");

        long connectStart = System.nanoTime();
        chirp.connect(redisUsername, redisPort, redisPassword);
        long connectEnd = System.nanoTime();
        ChirpLogger.debug(
                "ChirpBuilder: Redis connection completed in "
                        + (connectEnd - connectStart) / 1_000_000.0
                        + "ms.");

        long subscribeStart = System.nanoTime();
        chirp.subscribe();
        long subscribeEnd = System.nanoTime();
        ChirpLogger.debug(
                "ChirpBuilder: Redis subscription initiated in "
                        + (subscribeEnd - subscribeStart) / 1_000_000.0
                        + "ms.");

        long callbackThreadStart = System.nanoTime();
        chirp.setupCallbackRemoverThread();
        long callbackThreadEnd = System.nanoTime();
        ChirpLogger.debug(
                "ChirpBuilder: Callback remover thread setup in "
                        + (callbackThreadEnd - callbackThreadStart) / 1_000_000.0
                        + "ms.");

        long endTime = System.currentTimeMillis();
        ChirpLogger.info("Chirp build process completed in " + (endTime - startTime) + "ms.");
        return chirp;
    }
}
