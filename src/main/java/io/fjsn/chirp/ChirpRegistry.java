package io.fjsn.chirp;

import io.fjsn.chirp.annotation.ChirpHandler;
import io.fjsn.chirp.annotation.ChirpListener;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.converter.impl.BooleanConverter;
import io.fjsn.chirp.converter.impl.ByteConverter;
import io.fjsn.chirp.converter.impl.CharacterConverter;
import io.fjsn.chirp.converter.impl.DoubleConverter;
import io.fjsn.chirp.converter.impl.FloatConverter;
import io.fjsn.chirp.converter.impl.IntegerConverter;
import io.fjsn.chirp.converter.impl.ListConverter;
import io.fjsn.chirp.converter.impl.LongConverter;
import io.fjsn.chirp.converter.impl.MapConverter;
import io.fjsn.chirp.converter.impl.SetConverter;
import io.fjsn.chirp.converter.impl.ShortConverter;
import io.fjsn.chirp.converter.impl.StringConverter;
import io.fjsn.chirp.converter.impl.UUIDConverter;
import io.fjsn.chirp.internal.callback.CallbackManager;
import io.fjsn.chirp.internal.handler.HandlerMethod;
import io.fjsn.chirp.internal.schema.ObjectSchema;
import io.fjsn.chirp.internal.schema.PacketSchema;
import io.fjsn.chirp.internal.schema.SchemaGenerator;
import io.fjsn.chirp.internal.util.ChirpLogger;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChirpRegistry {

    private final Map<String, FieldConverter<?>> converterRegistry;
    private final Map<String, Class<?>> packetRegistry;
    private final Map<Object, List<HandlerMethod>> listenerRegistry;

    private final Map<String, PacketSchema> packetSchemaRegistry;
    private final Map<String, ObjectSchema> objectSchemaRegistry;
    private final ConcurrentHashMap<String, Boolean> inProgressSchemas;

    private final SchemaGenerator schemaGenerator;
    private final CallbackManager callbackManager;

    public static String normalizeTypeName(Type type) {
        if (type instanceof Class<?> clazz) {
            return normalizeTypeName(clazz);
        }

        if (type instanceof ParameterizedType pt) {
            StringBuilder builder = new StringBuilder();

            builder.append(normalizeTypeName(pt.getRawType()));

            builder.append("<");
            Type[] args = pt.getActualTypeArguments();
            for (int i = 0; i < args.length; i++) {
                builder.append(normalizeTypeName(args[i]));
                if (i < args.length - 1) builder.append(",");
            }
            builder.append(">");
            return builder.toString().toUpperCase();
        }

        return type.getTypeName().toUpperCase();
    }

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

        String name = clazz.getCanonicalName() != null ? clazz.getCanonicalName() : clazz.getName();
        return name.replace('.', '_').replace('$', '_').toUpperCase();
    }

    public ChirpRegistry() {
        this.packetRegistry = new ConcurrentHashMap<>();
        this.converterRegistry = new ConcurrentHashMap<>();
        this.listenerRegistry = new ConcurrentHashMap<>();

        this.packetSchemaRegistry = new ConcurrentHashMap<>();
        this.objectSchemaRegistry = new ConcurrentHashMap<>();
        this.inProgressSchemas = new ConcurrentHashMap<>();

        this.schemaGenerator =
                new SchemaGenerator(
                        this.converterRegistry,
                        this.packetSchemaRegistry,
                        this.objectSchemaRegistry,
                        this.inProgressSchemas);
        this.callbackManager = new CallbackManager();
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

    public Map<UUID, ChirpCallback<?>> getCallbackRegistry() {
        return callbackManager.getCallbackRegistry();
    }

    public Map<String, PacketSchema> getPacketSchemaRegistry() {
        return packetSchemaRegistry;
    }

    public Map<String, ObjectSchema> getObjectSchemaRegistry() {
        return objectSchemaRegistry;
    }

    public void registerDefaultConverters() {
        long startTime = System.currentTimeMillis();
        registerConverter(boolean.class, new BooleanConverter());
        registerConverter(Boolean.class, new BooleanConverter());

        registerConverter(byte.class, new ByteConverter());
        registerConverter(Byte.class, new ByteConverter());

        registerConverter(char.class, new CharacterConverter());
        registerConverter(Character.class, new CharacterConverter());

        registerConverter(double.class, new DoubleConverter());
        registerConverter(Double.class, new DoubleConverter());

        registerConverter(float.class, new FloatConverter());
        registerConverter(Float.class, new FloatConverter());

        registerConverter(int.class, new IntegerConverter());
        registerConverter(Integer.class, new IntegerConverter());

        registerConverter(long.class, new LongConverter());
        registerConverter(Long.class, new LongConverter());

        registerConverter(short.class, new ShortConverter());
        registerConverter(Short.class, new ShortConverter());

        registerConverter(String.class, new StringConverter());
        registerConverter(UUID.class, new UUIDConverter());

        registerConverter(List.class, new ListConverter());
        registerConverter(Set.class, new SetConverter());
        registerConverter(Map.class, new MapConverter());
        long endTime = System.currentTimeMillis();
        ChirpLogger.info("Registered default converters in " + (endTime - startTime) + "ms.");
    }

    public void registerConverter(Class<?> genericType, FieldConverter<?> converter) {
        long startTime = System.nanoTime();
        if (genericType == null) {
            throw new IllegalArgumentException("Converter type cannot be null");
        }

        if (converter == null) {
            throw new IllegalArgumentException("Converter cannot be null");
        }

        String type = normalizeTypeName(genericType);
        if (converterRegistry.containsKey(type)) {
            ChirpLogger.warning(
                    "Converter type '"
                            + type
                            + "' is already registered. Skipping re-registration.");
            return;
        }

        converterRegistry.put(type, converter);
        long endTime = System.nanoTime();
        ChirpLogger.debug(
                "Registered converter for "
                        + type
                        + " in "
                        + (endTime - startTime) / 1_000_000.0
                        + "ms.");
    }

    public void registerPacket(Class<?> packetClass) {
        this.schemaGenerator.registerPacket(packetClass);

        String type =
                packetClass.getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
        if (packetRegistry.containsKey(type)) {
            ChirpLogger.warning(
                    "Packet class '"
                            + type
                            + "' is already registered in ChirpRegistry. Skipping.");
            return;
        }
        this.packetRegistry.put(type, packetClass);
    }

    public void registerListener(Object listenerInstance) {
        long startTime = System.nanoTime();
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
            ChirpLogger.warning(
                    "Listener " + listenerClass.getName() + " has no @ChirpHandler methods");
        }

        String name =
                listenerClass.getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();

        listenerRegistry.put(listenerInstance, handlerMethods);
        long endTime = System.nanoTime();
        ChirpLogger.debug(
                "Registered listener "
                        + name
                        + " in "
                        + (endTime - startTime) / 1_000_000.0
                        + "ms.");
    }

    public void registerCallback(UUID packetId, ChirpCallback<?> callback) {
        callbackManager.registerCallback(packetId, callback);
    }

    public void setupCallbackRemoverThread() {
        callbackManager.setupCallbackRemoverThread();
    }

    private List<HandlerMethod> findHandlerMethods(Class<?> listenerClass) {
        long startTime = System.nanoTime();
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
                                + " must have a parameterized type (e.g.,"
                                + " ChirpPacketEvent<MyPacket>)");
            }

            ParameterizedType pType = (ParameterizedType) genericParamType;
            Type argType = pType.getActualTypeArguments()[0];

            if (!(argType instanceof Class<?>)) {
                throw new IllegalArgumentException(
                        "Handler method "
                                + method.getName()
                                + " in "
                                + listenerClass.getName()
                                + " must have a valid concrete class as its generic type argument"
                                + " (e.g., ChirpPacketEvent<MyPacket.class>)");
            }

            Class<?> genericArgument = (Class<?>) argType;

            handlerMethods.add(new HandlerMethod(method, genericArgument));
        }

        long endTime = System.nanoTime();
        ChirpLogger.debug(
                "Found "
                        + handlerMethods.size()
                        + " handler methods for "
                        + listenerClass.getName()
                        + " in "
                        + (endTime - startTime) / 1_000_000.0
                        + "ms.");
        return Collections.unmodifiableList(handlerMethods);
    }

    public void cleanup() {
        packetRegistry.clear();
        listenerRegistry.clear();
        converterRegistry.clear();

        schemaGenerator.cleanup();
        callbackManager.cleanup();

        ChirpLogger.debug("ChirpRegistry: Cleared all registrations and delegated cleanup.");
    }
}
