package io.fjsn.chirp;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.annotation.ChirpHandler;
import io.fjsn.chirp.annotation.ChirpListener;
import io.fjsn.chirp.annotation.ChirpPacket;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.converter.impl.BooleanConverter;
import io.fjsn.chirp.converter.impl.ByteConverter;
import io.fjsn.chirp.converter.impl.CharacterConverter;
import io.fjsn.chirp.converter.impl.DoubleConverter;
import io.fjsn.chirp.converter.impl.FloatConverter;
import io.fjsn.chirp.converter.impl.IntegerConverter;
import io.fjsn.chirp.converter.impl.LongConverter;
import io.fjsn.chirp.converter.impl.ShortConverter;
import io.fjsn.chirp.converter.impl.StringConverter;
import io.fjsn.chirp.converter.impl.UUIDConverter;
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
import java.util.UUID;
import java.util.logging.Level;

public class ChirpRegistry {

    private final Map<String, FieldConverter<?>> converterRegistry;
    private final Map<String, Class<?>> packetRegistry;
    private final Map<Object, List<HandlerMethod>> listenerRegistry;

    public static String normalizeTypeName(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            if (clazz == byte.class) return "BYTE";
            if (clazz == short.class) return "SHORT";
            if (clazz == int.class) return "INTEGER";
            if (clazz == long.class) return "LONG";
            if (clazz == float.class) return "FLOAT";
            if (clazz == double.class) return "DOUBLE";
            if (clazz == boolean.class) return "BOOLEAN";
            if (clazz == char.class) return "CHARACTER";
        }
        return clazz.getSimpleName().toUpperCase();
    }

    public ChirpRegistry() {
        this.packetRegistry = new HashMap<>();
        this.converterRegistry = new HashMap<>();
        this.listenerRegistry = new HashMap<>();
    }

    public Map<String, FieldConverter<?>> getConverterRegistry() {
        return converterRegistry;
    }

    public Map<String, Class<?>> getPacketRegistry() {
        return packetRegistry;
    }

    public Map<Object, List<HandlerMethod>> getListenerRegistry() {
        return listenerRegistry;
    }

    public void registerDefaultConverters() {
        registerConverter(Boolean.class, new BooleanConverter());
        registerConverter(Byte.class, new ByteConverter());
        registerConverter(Character.class, new CharacterConverter());
        registerConverter(Double.class, new DoubleConverter());
        registerConverter(Float.class, new FloatConverter());
        registerConverter(Integer.class, new IntegerConverter());
        registerConverter(Long.class, new LongConverter());
        registerConverter(Short.class, new ShortConverter());
        registerConverter(String.class, new StringConverter());
        registerConverter(UUID.class, new UUIDConverter());
    }

    public void registerConverter(Class<?> genericType, FieldConverter<?> converter) {
        if (genericType == null) {
            throw new IllegalArgumentException("Converter type cannot be null");
        }

        if (converter == null) {
            throw new IllegalArgumentException("Converter cannot be null");
        }

        String type = normalizeTypeName(genericType);
        if (converterRegistry.containsKey(type)) {
            throw new IllegalArgumentException(
                    "Converter type '" + type + "' is already registered");
        }

        converterRegistry.put(type, converter);
        Chirp.CHIRP_LOGGER.log(Level.FINE, "Registered converter: " + type);
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
        Chirp.CHIRP_LOGGER.log(Level.FINE, "Registered packet: " + type);
    }

    public void registerListener(Object listenerInstance) {
        if (listenerInstance == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }

        Class<?> listenerClass = listenerInstance.getClass();

        if (!listenerClass.isAnnotationPresent(ChirpListener.class)) {
            throw new IllegalArgumentException(
                    "Listener class must be annotated with @ChirpListener");
        }

        if (listenerRegistry.containsKey(listenerInstance)) {
            throw new IllegalArgumentException(
                    "Listener '" + listenerClass.getSimpleName() + "' is already registered");
        }

        List<HandlerMethod> handlerMethods = findHandlerMethods(listenerClass);

        if (handlerMethods.isEmpty()) {
            Chirp.CHIRP_LOGGER.log(
                    Level.WARNING,
                    "Listener " + listenerClass.getName() + " has no @ChirpHandler methods");
        }

        listenerRegistry.put(listenerInstance, handlerMethods);
        Chirp.CHIRP_LOGGER.log(Level.FINE, "Registered listener: " + listenerClass.getSimpleName());
    }

    private List<HandlerMethod> findHandlerMethods(Class<?> listenerClass) {
        List<HandlerMethod> handlerMethods = new ArrayList<>();

        for (Method method : listenerClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(ChirpHandler.class)) continue;

            if (method.getParameterCount() != 1) {
                throw new IllegalArgumentException(
                        "Handler method "
                                + method.getName()
                                + " in "
                                + listenerClass.getName()
                                + " must have exactly one parameter");
            }

            Class<?> paramType = method.getParameterTypes()[0];

            if (!(paramType.isAssignableFrom(ChirpPacketEvent.class))) {
                throw new IllegalArgumentException(
                        "Handler method "
                                + method.getName()
                                + " in "
                                + listenerClass.getName()
                                + " must accept a ChirpPacketEvent parameter");
            }

            Type genericParamType = method.getGenericParameterTypes()[0];

            if (!(genericParamType instanceof ParameterizedType)) {
                throw new IllegalArgumentException(
                        "Handler method "
                                + method.getName()
                                + " in "
                                + listenerClass.getName()
                                + " must have a parameterized type");
            }

            ParameterizedType pType = (ParameterizedType) genericParamType;
            Type argType = pType.getActualTypeArguments()[0];

            if (!(argType instanceof Class<?>)) {
                throw new IllegalArgumentException(
                        "Handler method "
                                + method.getName()
                                + " in "
                                + listenerClass.getName()
                                + " must have a valid generic type argument");
            }

            Class<?> genericArgument = (Class<?>) argType;
            handlerMethods.add(new HandlerMethod(method, genericArgument));
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

    public void scan(String packageName) {
        Reflections reflections = new Reflections(packageName, Scanners.TypesAnnotated);

        for (Class<?> packetClass : reflections.getTypesAnnotatedWith(ChirpPacket.class)) {
            try {
                registerPacket(packetClass);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(
                        "Failed to register packet: "
                                + packetClass.getName()
                                + " - "
                                + e.getMessage());
            }
        }

        for (Class<?> converterClass : reflections.getTypesAnnotatedWith(ChirpConverter.class)) {
            ChirpConverter chirpConverterAnnotation =
                    converterClass.getAnnotation(ChirpConverter.class);
            if (!chirpConverterAnnotation.scan()) return;

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
                throw new RuntimeException(
                        "Failed to register converter: "
                                + converterClass.getName()
                                + " - "
                                + e.getMessage());
            }
        }

        for (Class<?> listenerClass : reflections.getTypesAnnotatedWith(ChirpListener.class)) {
            ChirpListener chirpListenerAnnotation =
                    listenerClass.getAnnotation(ChirpListener.class);
            if (!chirpListenerAnnotation.scan()) return;

            try {
                Object listenerInstance = listenerClass.getDeclaredConstructor().newInstance();
                registerListener(listenerInstance);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to register listener: "
                                + listenerClass.getName()
                                + " - "
                                + e.getMessage());
            }
        }
    }

    public void cleanup() {
        packetRegistry.clear();
        listenerRegistry.clear();
        converterRegistry.clear();
        Chirp.CHIRP_LOGGER.log(Level.FINE, "Cleared all registrations");
    }
}
