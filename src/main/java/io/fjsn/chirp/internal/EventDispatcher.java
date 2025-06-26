package io.fjsn.chirp.internal;

import io.fjsn.chirp.ChirpPacketEvent;
import io.fjsn.chirp.ChirpRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class EventDispatcher {

    private final ChirpRegistry registry;

    public EventDispatcher(ChirpRegistry registry) {
        this.registry = registry;
    }

    public void dispatchEvent(ChirpPacketEvent<?> event) {
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
}
