package io.fjsn.chirp;

import io.fjsn.chirp.converter.FieldConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

public class ChirpBuilder {

    private Level logLevel;
    private String channel;
    private String origin;
    private String scanPackageName;

    private String redisUsername;
    private int redisPort;
    private String redisPassword;

    private Map<Class<?>, FieldConverter<?>> converters;
    private List<Class<?>> packetClasses;
    private ArrayList<Object> listenerObjects;

    public ChirpBuilder() {
        packetClasses = new ArrayList<>();
        listenerObjects = new ArrayList<>();
        converters = new HashMap<>();
    }

    public ChirpBuilder logLevel(Level level) {
        this.logLevel = level;
        return this;
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

    public Chirp build() {
        if (channel == null || channel.isEmpty()) {
            throw new RuntimeException("Channel must be set");
        }
        if (origin == null || origin.isEmpty()) {
            throw new RuntimeException("Origin must be set");
        }

        Chirp chirp = origin != null ? new Chirp(channel, origin) : new Chirp(channel);
        chirp.setLogLevel(logLevel);

        if (scanPackageName != null) chirp.scan(scanPackageName);
        for (Class<?> clazz : packetClasses) chirp.registerPacket(clazz);
        for (Object obj : listenerObjects) chirp.registerListener(obj);
        for (Entry<Class<?>, FieldConverter<?>> entry : converters.entrySet())
            chirp.registerConverter(entry.getKey(), entry.getValue());

        chirp.connect(redisUsername, redisPort, redisPassword);
        chirp.subscribe();
        return chirp;
    }
}
