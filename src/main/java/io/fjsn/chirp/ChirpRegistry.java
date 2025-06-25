package io.fjsn.chirp;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.annotation.ChirpHandler;
import io.fjsn.chirp.annotation.ChirpListener;
import io.fjsn.chirp.annotation.ChirpPacket;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.internal.HandlerMethod;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChirpRegistry {

    private final Map<String, Class<?>> packetRegistry;
    private final Map<String, FieldConverter<?>> converterRegistry;
    private final Map<Object, List<HandlerMethod>> listeners;

    public ChirpRegistry() {
        this.packetRegistry = new HashMap<>();
        this.converterRegistry = new HashMap<>();
        this.listeners = new HashMap<>();
    }

    public Map<String, Class<?>> getPacketRegistry() {
        return packetRegistry;
    }

    public Map<String, FieldConverter<?>> getConverterRegistry() {
        return converterRegistry;
    }

    public Map<Object, List<HandlerMethod>> getListeners() {
        return listeners;
    }

    public void registerPacket(Class<?> packetClass) {
        if (packetClass == null) {
            throw new IllegalArgumentException("Packet class cannot be null");
        }

        if (!packetClass.isAnnotationPresent(io.fjsn.chirp.annotation.ChirpPacket.class)) {
            throw new IllegalArgumentException("Packet class must be annotated with @ChirpPacket");
        }

        String type = packetClass.getSimpleName().toUpperCase();
        if (packetRegistry.containsKey(type)) {
            throw new IllegalArgumentException("Packet type '" + type + "' is already registered");
        }

        packetRegistry.put(type, packetClass);
        System.out.println("[ChirpRegistry] Registered packet: " + type);
    }

    public void registerConverter(Class<?> genericType, FieldConverter<?> converter) {
        if (genericType == null) {
            throw new IllegalArgumentException("Converter type cannot be null");
        }

        if (converter == null) {
            throw new IllegalArgumentException("Converter cannot be null");
        }

        String type = genericType.getSimpleName().toUpperCase();
        if (converterRegistry.containsKey(type)) {
            throw new IllegalArgumentException(
                    "Converter type '" + type + "' is already registered");
        }

        converterRegistry.put(type, converter);
        System.out.println("[ChirpRegistry] Registered converter: " + type);
    }

    public void addListener(Object listenerInstance) {
        if (listenerInstance == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }

        Class<?> listenerClass = listenerInstance.getClass();

        if (!listenerClass.isAnnotationPresent(ChirpListener.class)) {
            throw new IllegalArgumentException(
                    "Listener class must be annotated with @ChirpListener");
        }

        if (listeners.containsKey(listenerInstance)) {
            throw new IllegalArgumentException(
                    "Listener '" + listenerClass.getSimpleName() + "' is already registered");
        }

        List<HandlerMethod> handlerMethods = findHandlerMethods(listenerClass);

        if (handlerMethods.isEmpty()) {
            System.err.println(
                    "[ChirpRegistry] Listener "
                            + listenerClass.getName()
                            + " has no @ChirpHandler methods");
        }

        listeners.put(listenerInstance, handlerMethods);
        System.out.println("[ChirpRegistry] Registered listener: " + listenerClass.getSimpleName());
    }

    private List<HandlerMethod> findHandlerMethods(Class<?> listenerClass) {
        List<HandlerMethod> handlerMethods = new ArrayList<>();

        for (Method method : listenerClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(ChirpHandler.class)) continue;

            if (method.getParameterCount() == 1) {
                Class<?> paramType = method.getParameterTypes()[0];
                handlerMethods.add(new HandlerMethod(method, paramType));
            } else {
                System.err.println(
                        "[ChirpRegistry] @ChirpHandler method "
                                + method.getName()
                                + " in "
                                + listenerClass.getName()
                                + " has invalid parameter count");
            }
        }

        return handlerMethods;
    }

    private Class<?> getConverterGenericType(Class<?> converterClass) {
        for (Type iface : converterClass.getGenericInterfaces()) {
            if (iface instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) iface;
                if (paramType.getRawType() instanceof Class
                        && FieldConverter.class.isAssignableFrom(
                                (Class<?>) paramType.getRawType())) {
                    Type[] typeArgs = paramType.getActualTypeArguments();
                    if (typeArgs.length == 1) {
                        Type typeArg = typeArgs[0];
                        if (typeArg instanceof Class<?>) {
                            return (Class<?>) typeArg;
                        } else if (typeArg instanceof ParameterizedType) {
                            return (Class<?>) ((ParameterizedType) typeArg).getRawType();
                        }
                    }
                }
            }
        }
        return null;
    }

    public void scanAndRegister(String packageName) {
        Reflections reflections = new Reflections(packageName, Scanners.TypesAnnotated);

        for (Class<?> packetClass : reflections.getTypesAnnotatedWith(ChirpPacket.class)) {
            try {
                registerPacket(packetClass);
            } catch (IllegalArgumentException e) {
                System.err.println(
                        "[ChirpRegistry] Failed to register packet: "
                                + packetClass.getName()
                                + " - "
                                + e.getMessage());
            }
        }

        for (Class<?> converterClass : reflections.getTypesAnnotatedWith(ChirpConverter.class)) {
            try {
                if (!FieldConverter.class.isAssignableFrom(converterClass)) {
                    throw new IllegalArgumentException(
                            "Converter class "
                                    + converterClass.getName()
                                    + " does not implement FieldConverter");
                }

                Class<?> convertedType = getConverterGenericType(converterClass);
                if (convertedType == null) {
                    throw new IllegalArgumentException(
                            "Cannot determine generic type for converter: "
                                    + converterClass.getName());
                }

                FieldConverter<?> converterInstance =
                        (FieldConverter<?>) converterClass.getDeclaredConstructor().newInstance();

                registerConverter(convertedType, converterInstance);
            } catch (Exception e) {
                System.err.println(
                        "[ChirpRegistry] Failed to register converter: "
                                + converterClass.getName()
                                + " - "
                                + e.getMessage());
            }
        }

        for (Class<?> listenerClass : reflections.getTypesAnnotatedWith(ChirpListener.class)) {
            try {
                Object listenerInstance = listenerClass.getDeclaredConstructor().newInstance();
                addListener(listenerInstance);
            } catch (Exception e) {
                System.err.println(
                        "[ChirpRegistry] Failed to register listener: "
                                + listenerClass.getName()
                                + " - "
                                + e.getMessage());
            }
        }
    }

    public void cleanup() {
        packetRegistry.clear();
        converterRegistry.clear();
        listeners.clear();
        System.out.println("[ChirpRegistry] Cleared all registrations");
    }
}
