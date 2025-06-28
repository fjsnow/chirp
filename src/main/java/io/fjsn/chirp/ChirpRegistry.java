package io.fjsn.chirp;

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
import io.fjsn.chirp.converter.impl.ListConverter;
import io.fjsn.chirp.converter.impl.LongConverter;
import io.fjsn.chirp.converter.impl.MapConverter;
import io.fjsn.chirp.converter.impl.SetConverter;
import io.fjsn.chirp.converter.impl.ShortConverter;
import io.fjsn.chirp.converter.impl.StringConverter;
import io.fjsn.chirp.converter.impl.UUIDConverter;
import io.fjsn.chirp.internal.callback.CallbackManager; // NEW: Import CallbackManager
import io.fjsn.chirp.internal.util.ChirpLogger;
import io.fjsn.chirp.internal.handler.HandlerMethod;
import io.fjsn.chirp.internal.schema.ObjectSchema;
import io.fjsn.chirp.internal.schema.PacketSchema;
import io.fjsn.chirp.internal.schema.SchemaGenerator; // NEW: Import SchemaGenerator

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

/**
 * The central registry for Chirp components, including packet types, field converters, and event
 * listeners. It orchestrates the registration and lookup of these components, delegating complex
 * tasks like schema generation and callback management to specialized internal classes.
 */
public class ChirpRegistry {

    // Core Registries directly managed by ChirpRegistry
    private final Map<String, FieldConverter<?>> converterRegistry;
    private final Map<String, Class<?>> packetRegistry; // Maps simplified type names to Class<?>
    private final Map<Object, List<HandlerMethod>>
            listenerRegistry; // Maps listener instances to their handler methods

    // Schema-related registries - populated by SchemaGenerator, but still exposed by ChirpRegistry
    // for use by PacketSerializer. This allows SchemaGenerator to be private to ChirpRegistry,
    // while still making the necessary data available.
    private final Map<String, PacketSchema> packetSchemaRegistry;
    private final Map<String, ObjectSchema> objectSchemaRegistry;
    private final ConcurrentHashMap<String, Boolean>
            inProgressSchemas; // Shared with SchemaGenerator for recursion safety

    // Delegated Managers
    private final SchemaGenerator schemaGenerator;
    private final CallbackManager callbackManager;

    /**
     * Converts a generic {@link Type} into a normalized uppercase string key for registry lookup.
     * Handles both raw classes and parameterized types (e.g., List<String> becomes LIST<STRING>).
     *
     * @param type The Type to normalize.
     * @return A normalized uppercase string representation of the type.
     */
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

    /**
     * Converts a {@link Class} into a normalized uppercase string key for registry lookup. Handles
     * primitive types specifically and replaces problematic characters in class names.
     *
     * @param clazz The Class to normalize.
     * @return A normalized uppercase string representation of the class.
     */
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
        // Use getCanonicalName for nested classes (e.g., Outer.Inner) or fall back to getName
        String name = clazz.getCanonicalName() != null ? clazz.getCanonicalName() : clazz.getName();
        return name.replace('.', '_').replace('$', '_').toUpperCase();
    }

    /**
     * Constructs a new ChirpRegistry, initializing its internal registries and delegated managers.
     */
    public ChirpRegistry() {
        this.packetRegistry = new ConcurrentHashMap<>();
        this.converterRegistry = new ConcurrentHashMap<>();
        this.listenerRegistry = new ConcurrentHashMap<>();

        // Initialize maps that SchemaGenerator will populate
        this.packetSchemaRegistry = new ConcurrentHashMap<>();
        this.objectSchemaRegistry = new ConcurrentHashMap<>();
        this.inProgressSchemas = new ConcurrentHashMap<>();

        // Initialize delegated managers, passing necessary dependencies
        this.schemaGenerator =
                new SchemaGenerator(
                        this.converterRegistry,
                        this.packetSchemaRegistry,
                        this.objectSchemaRegistry,
                        this.inProgressSchemas);
        this.callbackManager = new CallbackManager();
    }

    /**
     * Returns the registry of custom field converters.
     *
     * @return A map where keys are normalized type names and values are {@link FieldConverter}
     *     instances.
     */
    public Map<String, FieldConverter<?>> getConverterRegistry() {
        return converterRegistry;
    }

    /**
     * Returns the registry of registered packet classes.
     *
     * @return A map where keys are normalized packet type names and values are the {@link Class}
     *     objects.
     */
    public Map<String, Class<?>> getPacketRegistry() {
        return packetRegistry;
    }

    /**
     * Returns the registry of registered listener instances and their handler methods.
     *
     * @return A map where keys are listener instances and values are lists of {@link HandlerMethod}
     *     objects.
     */
    public Map<Object, List<HandlerMethod>> getListenerRegistry() {
        return listenerRegistry;
    }

    /**
     * Returns the registry of active callbacks, delegated to {@link CallbackManager}.
     *
     * @return A map where keys are packet UUIDs and values are {@link ChirpCallback} instances.
     */
    public Map<UUID, ChirpCallback<?>> getCallbackRegistry() {
        return callbackManager.getCallbackRegistry();
    }

    /**
     * Returns the registry of pre-computed packet schemas, populated by {@link SchemaGenerator}.
     * This is primarily used by {@link io.fjsn.chirp.internal.PacketSerializer}.
     *
     * @return A map where keys are normalized packet type names and values are {@link PacketSchema}
     *     instances.
     */
    public Map<String, PacketSchema> getPacketSchemaRegistry() {
        return packetSchemaRegistry;
    }

    /**
     * Returns the registry of pre-computed object schemas for nested custom types, populated by
     * {@link SchemaGenerator}. This is primarily used by {@link
     * io.fjsn.chirp.internal.PacketSerializer}.
     *
     * @return A map where keys are normalized object type names and values are {@link ObjectSchema}
     *     instances.
     */
    public Map<String, ObjectSchema> getObjectSchemaRegistry() {
        return objectSchemaRegistry;
    }

    /**
     * Registers the default set of built-in field converters for common Java types (e.g., Boolean,
     * Integer, String, List, Map, Set).
     */
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

        // These converters handle parameterized types, but their registration key is for the raw
        // type
        registerConverter(List.class, new ListConverter());
        registerConverter(Set.class, new SetConverter());
        registerConverter(Map.class, new MapConverter());
        long endTime = System.currentTimeMillis();
        ChirpLogger.info("Registered default converters in " + (endTime - startTime) + "ms.");
    }

    /**
     * Registers a custom {@link FieldConverter} for a given generic type. If a converter for the
     * specified type is already registered, a warning is logged and the existing converter is kept.
     *
     * @param genericType The {@link Class} representing the generic type this converter handles.
     * @param converter The {@link FieldConverter} instance.
     * @throws IllegalArgumentException if genericType or converter is null.
     */
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

    /**
     * Registers a class as a Chirp packet. This method delegates the schema generation to {@link
     * SchemaGenerator} and also registers the packet class in the main {@code packetRegistry}.
     *
     * @param packetClass The {@link Class} to register as a packet.
     * @throws IllegalArgumentException if the packetClass is invalid (e.g., missing {@link
     *     ChirpPacket} annotation, no no-arg constructor), or already registered.
     */
    public void registerPacket(Class<?> packetClass) {
        // Delegate the complex schema generation and validation to SchemaGenerator
        this.schemaGenerator.registerPacket(packetClass);

        // Also register in the ChirpRegistry's own packet registry for quick lookup
        // by type name for dispatching.
        String type =
                packetClass.getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
        if (packetRegistry.containsKey(type)) {
            // This case might be hit if registerPacket is called explicitly after a scan,
            // or multiple times. SchemaGenerator handles its own idempotency.
            ChirpLogger.warning(
                    "Packet class '"
                            + type
                            + "' is already registered in ChirpRegistry. Skipping.");
            return;
        }
        this.packetRegistry.put(type, packetClass);
    }

    /**
     * Registers an object instance as a Chirp listener. The listener class must be annotated with
     * {@link ChirpListener}, and its methods handling events must be annotated with {@link
     * ChirpHandler} and accept a single {@link ChirpPacketEvent} parameter with a parameterized
     * type.
     *
     * @param listenerInstance The instance of the listener object to register.
     * @throws IllegalArgumentException if listenerInstance is null, or its class is not annotated
     *     with {@link ChirpListener}, or if its handler methods are improperly defined.
     */
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

    /**
     * Registers a callback, delegating the operation to {@link CallbackManager}.
     *
     * @param packetId The UUID of the packet for which this is a callback.
     * @param callback The {@link ChirpCallback} to register.
     * @throws IllegalArgumentException if packetId or callback is null.
     */
    public void registerCallback(UUID packetId, ChirpCallback<?> callback) {
        callbackManager.registerCallback(packetId, callback);
    }

    /**
     * Sets up the background thread for removing expired callbacks, delegating the operation to
     * {@link CallbackManager}.
     */
    public void setupCallbackRemoverThread() {
        callbackManager.setupCallbackRemoverThread();
    }

    /**
     * Finds and validates all methods annotated with {@link ChirpHandler} within a given listener
     * class.
     *
     * @param listenerClass The class to inspect for handler methods.
     * @return A list of {@link HandlerMethod} objects, each encapsulating a valid handler method.
     * @throws IllegalArgumentException if a method annotated with {@link ChirpHandler} does not
     *     meet the requirements (e.g., incorrect parameter count or type).
     */
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

    /**
     * Helper method to determine the generic type argument of a {@link FieldConverter}
     * implementation. This is used during automatic scanning of converters.
     *
     * @param converterClass The {@link Class} of the {@link FieldConverter} implementation.
     * @return The {@link Class} representing the generic type the converter handles, or null if it
     *     cannot be determined.
     */
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

    /**
     * Clears all registered packets, listeners, converters, and delegates cleanup to the {@link
     * SchemaGenerator} and {@link CallbackManager}. This method should be called when the Chirp
     * instance is being shut down to release all associated resources and clear state.
     */
    public void cleanup() {
        packetRegistry.clear();
        listenerRegistry.clear();
        converterRegistry.clear();

        // Delegate cleanup to specialized managers
        schemaGenerator.cleanup();
        callbackManager.cleanup();

        ChirpLogger.debug("ChirpRegistry: Cleared all registrations and delegated cleanup.");
    }
}
