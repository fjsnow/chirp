package io.fjsn.chirp.internal.callback;

import io.fjsn.chirp.ChirpCallback;
import io.fjsn.chirp.internal.util.ChirpLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CallbackManager {

    private final Map<UUID, ChirpCallback<?>> callbackRegistry;
    private Thread callbackRemoverThread;
    private volatile boolean running = false;

    public CallbackManager() {
        this.callbackRegistry = new ConcurrentHashMap<>();
    }

    public Map<UUID, ChirpCallback<?>> getCallbackRegistry() {
        return callbackRegistry;
    }

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
                                    Thread.sleep(20L);
                                    removeExpiredCallbacks();
                                } catch (InterruptedException e) {
                                    ChirpLogger.info("Callback remover thread interrupted.");
                                    Thread.currentThread().interrupt();
                                    break;
                                } catch (Exception e) {
                                    ChirpLogger.severe(
                                            "Error in callback remover thread: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                            ChirpLogger.debug("Callback remover thread stopped.");
                        },
                        "Chirp-CallbackRemover");

        callbackRemoverThread.setDaemon(true);
        callbackRemoverThread.start();
        ChirpLogger.debug("Callback remover thread started.");
    }

    public void cleanup() {
        running = false;
        if (callbackRemoverThread != null && callbackRemoverThread.isAlive()) {
            callbackRemoverThread.interrupt();
            try {
                callbackRemoverThread.join(1000);
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
