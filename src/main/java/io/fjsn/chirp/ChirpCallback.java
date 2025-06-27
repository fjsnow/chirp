package io.fjsn.chirp;

import java.util.function.Consumer;

public class ChirpCallback<T> {

    public static <T> ChirpCallback<T> of(Consumer<ChirpPacketEvent<T>> onResponse) {
        return new ChirpCallback<>(onResponse, () -> {}, 200L);
    }

    public static <T> ChirpCallback<T> of(Consumer<ChirpPacketEvent<T>> onResponse, long ttl) {
        return new ChirpCallback<>(onResponse, () -> {}, ttl);
    }

    public static <T> ChirpCallback<T> of(
            Consumer<ChirpPacketEvent<T>> onResponse, Runnable onTimeout) {
        return new ChirpCallback<>(onResponse, onTimeout, 200L);
    }

    public static <T> ChirpCallback<T> of(
            Consumer<ChirpPacketEvent<T>> onResponse, Runnable onTimeout, long ttl) {
        return new ChirpCallback<>(onResponse, onTimeout, ttl);
    }

    private final Consumer<ChirpPacketEvent<T>> onResponse;
    private final Runnable onTimeout;
    private final long expiration;

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

    public ChirpCallback(Consumer<ChirpPacketEvent<T>> onResponse, Runnable onTimeout) {
        this(onResponse, onTimeout, 200L);
    }

    public ChirpCallback(Consumer<ChirpPacketEvent<T>> onResponse, Runnable onTimeout, long ttl) {
        this.onResponse = onResponse;
        this.onTimeout = onTimeout;
        this.expiration = System.currentTimeMillis() + ttl;
    }
}
