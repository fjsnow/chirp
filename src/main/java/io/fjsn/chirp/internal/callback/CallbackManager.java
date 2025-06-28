package io.fjsn.chirp.internal.callback;

import io.fjsn.chirp.ChirpCallback;
import io.fjsn.chirp.internal.util.ChirpLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of {@link ChirpCallback} instances, including their registration,
 * expiration, and the periodic removal of expired callbacks.
 */
public class CallbackManager {

    private final Map<UUID, ChirpCallback<?>> callbackRegistry;
    private Thread callbackRemoverThread;
    private volatile boolean running = false;

    /** Constructs a new CallbackManager. */
    public CallbackManager() {
        this.callbackRegistry = new ConcurrentHashMap<>();
    }

    /**
     * Retrieves the internal registry of active callbacks.
     *
     * @return A map of packet IDs to their corresponding ChirpCallback instances.
     */
    public Map<UUID, ChirpCallback<?>> getCallbackRegistry() {
        return callbackRegistry;
    }

    /**
     * Registers a new callback associated with a specific packet ID.
     *
     * @param packetId The unique ID of the packet for which this callback is awaiting a response.
     * @param callback The {@link ChirpCallback} instance to register.
     * @throws IllegalArgumentException if packetId or callback is null.
     */
    public void registerCallback(UUID packetId, ChirpCallback<?> callback) {
        long startTime = System.nanoTime();
        if (packetId == null) {
            throw new IllegalArgumentException("Packet ID cannot be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null");
        }

        callbackRegistry.put(packetId, callback);
        long endTime = System.nanoTime();
        ChirpLogger.debug(
                "CallbackManager: Registered callback for packet "
                        + packetId
                        + " in "
                        + (endTime - startTime) / 1_000_000.0
                        + "ms.");
    }

    /**
     * Scans the registered callbacks and removes any that have expired. If a multi-response
     * callback expires with collected responses, its {@code onMultipleResponse} consumer is
     * invoked. Otherwise, or for single-response callbacks, the {@code onSingleTimeout} runnable is
     * executed.
     */
    public void removeExpiredCallbacks() {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, ChirpCallback<?>> entry : callbackRegistry.entrySet()) {
            UUID packetId = entry.getKey();
            ChirpCallback<?> callback = entry.getValue();

            if (callback.isExpired()) {
                ChirpLogger.debug(
                        "CallbackManager: Callback " + packetId + " expired. Handling timeout.");
                if (callback.isCollectingResponses()) {
                    if (!callback.getCollectedResponses().isEmpty()) {
                        ChirpLogger.debug(
                                "CallbackManager: Callback "
                                        + packetId
                                        + " expired with "
                                        + callback.getCollectedResponses().size()
                                        + " collected responses. Invoking onResponseList.");
                        try {
                            @SuppressWarnings("unchecked")
                            ChirpCallback<Object> typedCallback = (ChirpCallback<Object>) callback;
                            typedCallback
                                    .getOnMultipleResponse()
                                    .accept(typedCallback.getCollectedResponses());
                        } catch (Exception e) {
                            ChirpLogger.severe(
                                    "CallbackManager: Error invoking onResponseList for expired"
                                            + " callback "
                                            + packetId
                                            + ": "
                                            + e.getMessage());
                        }
                    } else {
                        ChirpLogger.debug(
                                "CallbackManager: Callback "
                                        + packetId
                                        + " expired with no collected responses. Invoking"
                                        + " onTimeout.");
                        callback.getOnSingleTimeout().run();
                    }
                } else {
                    ChirpLogger.debug(
                            "CallbackManager: Single response callback "
                                    + packetId
                                    + " expired. Invoking onTimeout.");
                    callback.getOnSingleTimeout().run();
                }
                toRemove.add(packetId);
            }
        }

        for (UUID packetId : toRemove) {
            callbackRegistry.remove(packetId);
            ChirpLogger.debug("CallbackManager: Removed expired callback: " + packetId);
        }
    }

    /**
     * Starts a dedicated background thread that periodically calls {@link
     * #removeExpiredCallbacks()}. This ensures that callbacks are cleaned up even if no new
     * messages are received. The thread is set as a daemon thread, meaning it will not prevent the
     * JVM from exiting. If the thread is already running, this method does nothing.
     */
    public void setupCallbackRemoverThread() {
        if (running) {
            ChirpLogger.warning("Callback remover thread is already running.");
            return;
        }

        running = true;
        callbackRemoverThread =
                new Thread(
                        () -> {
                            while (running && !Thread.currentThread().isInterrupted()) {
                                try {
                                    Thread.sleep(20L); // Check every 20ms
                                    removeExpiredCallbacks();
                                } catch (InterruptedException e) {
                                    ChirpLogger.info("Callback remover thread interrupted.");
                                    Thread.currentThread().interrupt(); // Restore interrupt status
                                    break;
                                } catch (Exception e) {
                                    ChirpLogger.severe(
                                            "Error in callback remover thread: " + e.getMessage());
                                    e.printStackTrace(); // Log stack trace for unexpected errors
                                }
                            }
                            ChirpLogger.debug("Callback remover thread stopped.");
                        },
                        "Chirp-CallbackRemover");

        callbackRemoverThread.setDaemon(true); // Allow JVM to exit if only daemon threads remain
        callbackRemoverThread.start();
        ChirpLogger.debug("Callback remover thread started.");
    }

    /**
     * Shuts down the callback remover thread and clears all registered callbacks. This method
     * should be called during the application shutdown to release resources.
     */
    public void cleanup() {
        running = false; // Signal thread to stop
        if (callbackRemoverThread != null && callbackRemoverThread.isAlive()) {
            callbackRemoverThread.interrupt();
            try {
                callbackRemoverThread.join(1000); // Wait up to 1 second for the thread to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ChirpLogger.warning(
                        "Interrupted while waiting for callback remover thread to join.");
            }
            callbackRemoverThread = null;
        }
        callbackRegistry.clear();
        ChirpLogger.debug("CallbackManager: Cleared all callbacks and stopped remover thread.");
    }
}
