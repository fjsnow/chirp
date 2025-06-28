package io.fjsn.chirp.internal;

import io.fjsn.chirp.ChirpCallback;
import io.fjsn.chirp.ChirpPacketEvent;
import io.fjsn.chirp.ChirpRegistry;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EventDispatcher {

    private final ChirpRegistry registry;

    public EventDispatcher(ChirpRegistry registry) {
        this.registry = registry;
    }

    public void dispatchEventToListeners(ChirpPacketEvent<?> event) {
        Class<?> packetClass = event.getPacket().getClass();
        Map<Object, List<HandlerMethod>> listenerMap = registry.getListenerRegistry();

        for (Map.Entry<Object, List<HandlerMethod>> entry : listenerMap.entrySet()) {

            Object listenerInstance = entry.getKey();
            List<HandlerMethod> handlerMethods = entry.getValue();
            for (HandlerMethod handlerMethod : handlerMethods) {
                MethodHandle methodHandle = handlerMethod.methodHandle;

                if (!handlerMethod.expectedPacketClass.isAssignableFrom(packetClass)) continue;

                try {
                    methodHandle.invoke(listenerInstance, event);
                } catch (WrongMethodTypeException e) {
                    ChirpLogger.severe(
                            "MethodHandle invocation failed due to wrong method type for "
                                    + handlerMethod.methodHandle
                                    + ": "
                                    + e.getMessage());
                } catch (Throwable e) {
                    ChirpLogger.severe(
                            "Failed to invoke handler "
                                    + handlerMethod.methodHandle
                                    + " for listener "
                                    + listenerInstance.getClass().getName()
                                    + ": "
                                    + e.getMessage());
                    if (e instanceof InvocationTargetException) {
                        ChirpLogger.severe(
                                "  Cause: "
                                        + ((InvocationTargetException) e)
                                                .getTargetException()
                                                .getMessage());
                        ((InvocationTargetException) e).getTargetException().printStackTrace();
                    } else {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void dispatchEventToResponders(ChirpPacketEvent<?> event) {
        UUID respondingTo = event.getRespondingTo();
        if (respondingTo == null)
            throw new IllegalArgumentException(
                    "Event is not a response (missing respondingTo ID).");

        ChirpCallback<?> rawCallback = registry.getCallbackRegistry().get(respondingTo);
        if (rawCallback == null) {
            ChirpLogger.warning(
                    "No callback was found for ID, perhaps it timed out or was already handled? ID:"
                            + " "
                            + respondingTo);
            return;
        }

        ChirpLogger.debug("Removing callback for ID: " + respondingTo);
        registry.getCallbackRegistry().remove(respondingTo);

        try {
            Class<?> receivedPacketClass = event.getPacket().getClass();
            Class<?> expectedResponseClass = rawCallback.getExpectedResponseClass();

            if (!expectedResponseClass.isAssignableFrom(receivedPacketClass)) {
                ChirpLogger.severe(
                        "Response type mismatch for callback ID "
                                + respondingTo
                                + ": Expected "
                                + expectedResponseClass.getName()
                                + " but received "
                                + receivedPacketClass.getName());
                return;
            }

            @SuppressWarnings("unchecked")
            ChirpCallback<Object> responder = (ChirpCallback<Object>) rawCallback;
            @SuppressWarnings("unchecked")
            ChirpPacketEvent<Object> typedEvent = (ChirpPacketEvent<Object>) event;
            responder.getOnResponse().accept(typedEvent);
        } catch (Exception e) {
            ChirpLogger.severe(
                    "Failed to invoke callback consumer for ID "
                            + respondingTo
                            + ": "
                            + e.getMessage());
        }
    }
}
