package io.fjsn.chirp.internal;

import io.fjsn.chirp.ChirpCallback;
import io.fjsn.chirp.ChirpPacketEvent;
import io.fjsn.chirp.ChirpRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
                Method method = handlerMethod.method;
                Class<?>[] params = method.getParameterTypes();

                if (params.length != 1) continue;
                if (!ChirpPacketEvent.class.isAssignableFrom(params[0])) continue;
                if (!handlerMethod.expectedPacketClass.isAssignableFrom(packetClass)) continue;

                try {
                    method.invoke(listenerInstance, event);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    ChirpLogger.severe("Failed to invoke handler: " + e.getMessage());
                }
            }
        }
    }

    public void dispatchEventToResponders(ChirpPacketEvent<?> event) {
        UUID respondingTo = event.getRespondingTo();
        if (respondingTo == null) throw new IllegalArgumentException("Event is not a response");

        ChirpCallback<?> rawCallback = registry.getCallbackRegistry().get(respondingTo);
        if (rawCallback == null) {
            ChirpLogger.warning(
                    "No callback was found for ID, perhaps it timed out? ID: " + respondingTo);
            return;
        }

        ChirpLogger.debug("Removing callback for ID: " + respondingTo);
        registry.getCallbackRegistry().remove(respondingTo);

        try {
            @SuppressWarnings("unchecked")
            ChirpCallback<Object> responder = (ChirpCallback<Object>) rawCallback;
            @SuppressWarnings("unchecked")
            ChirpPacketEvent<Object> typedEvent = (ChirpPacketEvent<Object>) event;
            responder.getOnResponse().accept(typedEvent);
        } catch (Exception e) {
            ChirpLogger.severe("Failed to invoke callback method: " + e.getMessage());
        }
    }
}
