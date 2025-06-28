package io.fjsn.chirp;

import java.util.function.Consumer;

public class ChirpCallback<T> {

    public static <T> ChirpCallback<T> of(
            Class<T> expectedResponseClass, Consumer<ChirpPacketEvent<T>> onResponse) {
        return new ChirpCallback<>(expectedResponseClass, onResponse, () -> {}, 200L);
    }

    public static <T> ChirpCallback<T> of(
            Class<T> expectedResponseClass, Consumer<ChirpPacketEvent<T>> onResponse, long ttl) {
        return new ChirpCallback<>(expectedResponseClass, onResponse, () -> {}, ttl);
    }

    public static <T> ChirpCallback<T> of(
            Class<T> expectedResponseClass,
            Consumer<ChirpPacketEvent<T>> onResponse,
            Runnable onTimeout) {
        return new ChirpCallback<>(expectedResponseClass, onResponse, onTimeout, 200L);
    }

    public static <T> ChirpCallback<T> of(
            Class<T> expectedResponseClass,
            Consumer<ChirpPacketEvent<T>> onResponse,
            Runnable onTimeout,
            long ttl) {
        return new ChirpCallback<>(expectedResponseClass, onResponse, onTimeout, ttl);
    }

    private final Class<T> expectedResponseClass;
    private final Consumer<ChirpPacketEvent<T>> onResponse;
    private final Runnable onTimeout;
    private final long expiration;

    public Class<T> getExpectedResponseClass() {
        return expectedResponseClass;
    }

    public Consumer<ChirpPacketEvent<T>> getOnResponse() {
        return onResponse;
    }

    public Runnable getOnTimeout() {
        return onTimeout;
    }

    public long getExpiration() {
        return expiration;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiration;
    }

    public ChirpCallback(
            Class<T> expectedResponseClass,
            Consumer<ChirpPacketEvent<T>> onResponse,
            Runnable onTimeout) {
        this(expectedResponseClass, onResponse, onTimeout, 200L);
    }

    public ChirpCallback(
            Class<T> expectedResponseClass,
            Consumer<ChirpPacketEvent<T>> onResponse,
            Runnable onTimeout,
            long ttl) {
        this.expectedResponseClass = expectedResponseClass;
        this.onResponse = onResponse;
        this.onTimeout = onTimeout;
        this.expiration = System.currentTimeMillis() + ttl;
    }
}
