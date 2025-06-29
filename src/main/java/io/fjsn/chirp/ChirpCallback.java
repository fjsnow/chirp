package io.fjsn.chirp;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ChirpCallback<T> {

    private static final long DEFAULT_TTL = 200L;

    public static <T> ChirpCallback<T> ofSingle(
            Class<T> expectedResponseClass, Consumer<ChirpPacketEvent<T>> onResponse) {
        return new ChirpCallback<>(expectedResponseClass, onResponse, () -> {}, DEFAULT_TTL);
    }

    public static <T> ChirpCallback<T> ofSingle(
            Class<T> expectedResponseClass, Consumer<ChirpPacketEvent<T>> onResponse, long ttl) {
        return new ChirpCallback<>(expectedResponseClass, onResponse, () -> {}, ttl);
    }

    public static <T> ChirpCallback<T> ofSingle(
            Class<T> expectedResponseClass,
            Consumer<ChirpPacketEvent<T>> onResponse,
            Runnable onTimeout) {
        return new ChirpCallback<>(expectedResponseClass, onResponse, onTimeout, DEFAULT_TTL);
    }

    public static <T> ChirpCallback<T> ofSingle(
            Class<T> expectedResponseClass,
            Consumer<ChirpPacketEvent<T>> onResponse,
            Runnable onTimeout,
            long ttl) {
        return new ChirpCallback<>(expectedResponseClass, onResponse, onTimeout, ttl);
    }

    public static <T> ChirpCallback<T> ofMultiple(
            Class<T> expectedResponseClass,
            Consumer<List<ChirpPacketEvent<T>>> onResponseMultiple) {
        return new ChirpCallback<>(
                expectedResponseClass, onResponseMultiple, DEFAULT_TTL, Integer.MAX_VALUE);
    }

    public static <T> ChirpCallback<T> ofMultiple(
            Class<T> expectedResponseClass,
            Consumer<List<ChirpPacketEvent<T>>> onResponseMultiple,
            long ttl) {
        return new ChirpCallback<>(
                expectedResponseClass, onResponseMultiple, ttl, Integer.MAX_VALUE);
    }

    public static <T> ChirpCallback<T> ofMultiple(
            Class<T> expectedResponseClass,
            Consumer<List<ChirpPacketEvent<T>>> onResponseMultiple,
            int maxResponses) {
        return new ChirpCallback<>(
                expectedResponseClass, onResponseMultiple, DEFAULT_TTL, maxResponses);
    }

    public static <T> ChirpCallback<T> ofMultiple(
            Class<T> expectedResponseClass,
            Consumer<List<ChirpPacketEvent<T>>> onResponseMultiple,
            long ttl,
            int maxResponses) {
        return new ChirpCallback<>(expectedResponseClass, onResponseMultiple, ttl, maxResponses);
    }

    private final Class<T> expectedResponseClass;

    private final Consumer<ChirpPacketEvent<T>> onSingleResponse;
    private final Consumer<List<ChirpPacketEvent<T>>> onMultipleResponse;
    private final Runnable onSingleTimeout;
    private final long expiration;
    private final int maxResponses;

    private final List<ChirpPacketEvent<T>> collectedResponses;

    public ChirpCallback(
            Class<T> expectedResponseClass,
            Consumer<ChirpPacketEvent<T>> onSingleResponse,
            Runnable onSingleTimeout,
            long ttl) {
        this.expectedResponseClass = expectedResponseClass;
        this.onSingleResponse = onSingleResponse;
        this.onSingleTimeout = onSingleTimeout != null ? onSingleTimeout : () -> {};
        this.expiration = System.currentTimeMillis() + ttl;

        this.onMultipleResponse = null;
        this.maxResponses = 1;
        this.collectedResponses = new CopyOnWriteArrayList<>();
    }

    public ChirpCallback(
            Class<T> expectedResponseClass,
            Consumer<List<ChirpPacketEvent<T>>> onMultipleResponse,
            long ttl,
            int maxResponses) {
        this.expectedResponseClass = expectedResponseClass;
        this.onMultipleResponse = onMultipleResponse;
        this.expiration = System.currentTimeMillis() + ttl;
        this.maxResponses = maxResponses;
        this.collectedResponses = new CopyOnWriteArrayList<>();

        this.onSingleResponse = null;
        this.onSingleTimeout = () -> {};
    }

    public Class<T> getExpectedResponseClass() {
        return expectedResponseClass;
    }

    public Consumer<ChirpPacketEvent<T>> getOnSingleResponse() {
        return onSingleResponse;
    }

    public Consumer<List<ChirpPacketEvent<T>>> getOnMultipleResponse() {
        return onMultipleResponse;
    }

    public Runnable getOnSingleTimeout() {
        return onSingleTimeout;
    }

    public long getExpiration() {
        return expiration;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiration;
    }

    public boolean isCollectingResponses() {
        return onMultipleResponse != null;
    }

    public void addCollectedResponse(ChirpPacketEvent<T> event) {
        if (!isCollectingResponses()) {
            throw new IllegalStateException(
                    "This callback is not configured to collect multiple responses.");
        }
        collectedResponses.add(event);
    }

    public List<ChirpPacketEvent<T>> getCollectedResponses() {
        return collectedResponses;
    }

    public boolean hasReachedMaxResponses() {
        return isCollectingResponses() && collectedResponses.size() >= maxResponses;
    }
}
