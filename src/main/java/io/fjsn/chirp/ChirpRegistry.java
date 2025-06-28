package io.fjsn.chirp;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.annotation.ChirpField;
import io.fjsn.chirp.annotation.ChirpHandler;
import io.fjsn.chirp.annotation.ChirpListener;
import io.fjsn.chirp.annotation.ChirpPacket;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.converter.impl.BooleanConverter;
import io.fjsn.chirp.converter.impl.ByteConverter;
import io.fjsn.chirp.converter.impl.CharacterConverter;
import io.fjsn.chirp.converter.impl.DoubleConverter;
import io.fjsn.chirp.converter.impl.EnumConverter;
import io.fjsn.chirp.converter.impl.FloatConverter;
import io.fjsn.chirp.converter.impl.IntegerConverter;
import io.fjsn.chirp.converter.impl.ListConverter;
import io.fjsn.chirp.converter.impl.LongConverter;
import io.fjsn.chirp.converter.impl.MapConverter;
import io.fjsn.chirp.converter.impl.SetConverter;
import io.fjsn.chirp.converter.impl.ShortConverter;
import io.fjsn.chirp.converter.impl.StringConverter;
import io.fjsn.chirp.converter.impl.UUIDConverter;
import io.fjsn.chirp.internal.ChirpLogger;
import io.fjsn.chirp.internal.FieldSchema;
import io.fjsn.chirp.internal.HandlerMethod;
import io.fjsn.chirp.internal.ObjectSchema;
import io.fjsn.chirp.internal.PacketSchema;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ChirpRegistry {

    private final Map<String, FieldConverter<?>> converterRegistry;
    private final Map<String, Class<?>> packetRegistry;
    private final Map<Object, List<HandlerMethod>> listenerRegistry;

    private final Map<UUID, ChirpCallback<?>> callbackRegistry;
    private final Map<String, PacketSchema> packetSchemaRegistry;
    private final Map<String, ObjectSchema> objectSchemaRegistry;

    private final ConcurrentHashMap<String, Boolean> inProgressSchemas;

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
        this.callbackRegistry = new ConcurrentHashMap<>();
        this.packetSchemaRegistry = new ConcurrentHashMap<>();
        this.objectSchemaRegistry = new ConcurrentHashMap<>();
        this.inProgressSchemas = new ConcurrentHashMap<>();
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
        return callbackRegistry;
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

    private boolean needsObjectSchema(Class<?> clazz) {
        if (clazz == null
                || clazz.isPrimitive()
                || clazz.isArray()
                || clazz.isInterface()
                || clazz.getName().startsWith("java.lang")
                || clazz.isEnum()) {
            return false;
        }
        return !converterRegistry.containsKey(normalizeTypeName(clazz));
    }

    private void registerObjectSchema(Class<?> objectClass) {
        long startTime = System.nanoTime();

        if (objectClass.isEnum()) {
            String enumTypeKey = normalizeTypeName(objectClass);
            if (!converterRegistry.containsKey(enumTypeKey)) {
                ChirpLogger.debug(
                        "Registering EnumConverter for top-level enum class: "
                                + objectClass.getName());
                @SuppressWarnings({"unchecked", "rawtypes"})
                FieldConverter<?> enumConverter = new EnumConverter();
                converterRegistry.put(enumTypeKey, enumConverter);
            }
            return;
        }

        if (!needsObjectSchema(objectClass)) {
            return;
        }

        String typeKey = normalizeTypeName(objectClass);

        if (objectSchemaRegistry.containsKey(typeKey)) {
            return;
        }

        if (inProgressSchemas.putIfAbsent(typeKey, Boolean.TRUE) != null) {
            return;
        }

        try {
            Constructor<?> noArgsConstructor = objectClass.getDeclaredConstructor();
            noArgsConstructor.setAccessible(true);

            List<FieldSchema> fieldSchemas = new ArrayList<>();
            Set<Class<?>> nestedTypesToScan = new HashSet<>();

            for (Field field : objectClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(ChirpField.class)) {
                    FieldSchema fs = new FieldSchema(field);
                    fieldSchemas.add(fs);

                    Class<?> fieldRawType = fs.rawType;
                    String fieldRawTypeName = normalizeTypeName(fieldRawType);

                    if (fieldRawType.isEnum()) {
                        if (!converterRegistry.containsKey(fieldRawTypeName)) {
                            ChirpLogger.debug(
                                    "Registering EnumConverter for field enum: "
                                            + fieldRawType.getName());
                            @SuppressWarnings({"unchecked", "rawtypes"})
                            FieldConverter<?> enumConverter = new EnumConverter();
                            converterRegistry.put(fieldRawTypeName, enumConverter);
                        }
                        continue;
                    }

                    if (fs.genericType instanceof ParameterizedType pt) {
                        for (Type argType : pt.getActualTypeArguments()) {
                            if (argType instanceof Class<?> actualClass) {
                                if (needsObjectSchema(actualClass)) {
                                    nestedTypesToScan.add(actualClass);
                                }
                            }
                        }
                    } else if (needsObjectSchema(fieldRawType)) {
                        nestedTypesToScan.add(fieldRawType);
                    }
                }
            }

            ObjectSchema schema =
                    new ObjectSchema(
                            objectClass,
                            noArgsConstructor,
                            Collections.unmodifiableList(fieldSchemas));

            objectSchemaRegistry.put(typeKey, schema);
            long endTime = System.nanoTime();
            ChirpLogger.debug(
                    "Successfully registered ObjectSchema for "
                            + objectClass.getName()
                            + " in "
                            + (endTime - startTime) / 1_000_000.0
                            + "ms.");

            for (Class<?> nestedType : nestedTypesToScan) {
                registerObjectSchema(nestedType);
            }

        } catch (NoSuchMethodException e) {
            inProgressSchemas.remove(typeKey);
            ChirpLogger.severe(
                    "Failed to register schema for "
                            + objectClass.getName()
                            + ": No no-argument constructor found. "
                            + e.getMessage());
            throw new IllegalArgumentException(
                    "Object class "
                            + objectClass.getName()
                            + " must have a no-argument constructor to have a schema generated.",
                    e);
        } catch (Exception e) {
            inProgressSchemas.remove(typeKey);
            ChirpLogger.severe(
                    "Failed to register schema for "
                            + objectClass.getName()
                            + ": "
                            + e.getMessage());
            throw new RuntimeException(
                    "Failed to generate object schema for "
                            + objectClass.getName()
                            + ": "
                            + e.getMessage(),
                    e);
        } finally {
            inProgressSchemas.remove(typeKey);
        }
    }

    public void registerPacket(Class<?> packetClass) {
        long startTime = System.nanoTime();

        if (packetClass == null) {
            throw new IllegalArgumentException("Packet class cannot be null");
        }

        if (!packetClass.isAnnotationPresent(ChirpPacket.class)) {
            throw new IllegalArgumentException("Packet class must be annotated with @ChirpPacket");
        }

        String type =
                packetClass.getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
        String typeKey = normalizeTypeName(packetClass);

        if (packetRegistry.containsKey(type)) {
            throw new IllegalArgumentException("Packet type '" + type + "' is already registered");
        }
        if (packetSchemaRegistry.containsKey(type)) {
            throw new IllegalArgumentException(
                    "Packet schema for type '" + type + "' is already registered");
        }

        try {
            Constructor<?> noArgsConstructor = packetClass.getDeclaredConstructor();
            noArgsConstructor.setAccessible(true);

            List<FieldSchema> fieldSchemas = new ArrayList<>();
            Set<Class<?>> nestedTypesToScan = new HashSet<>();

            for (Field field : packetClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(ChirpField.class)) {
                    FieldSchema fs = new FieldSchema(field);
                    fieldSchemas.add(fs);

                    Class<?> fieldRawType = fs.rawType;
                    String fieldRawTypeName = normalizeTypeName(fieldRawType);

                    if (fieldRawType.isEnum()) {
                        if (!converterRegistry.containsKey(fieldRawTypeName)) {
                            ChirpLogger.debug(
                                    "Registering EnumConverter for packet field enum: "
                                            + fieldRawType.getName());
                            @SuppressWarnings({"unchecked", "rawtypes"})
                            FieldConverter<?> enumConverter = new EnumConverter();
                            converterRegistry.put(fieldRawTypeName, enumConverter);
                        }
                        continue;
                    }

                    if (fs.genericType instanceof ParameterizedType pt) {
                        for (Type argType : pt.getActualTypeArguments()) {
                            if (argType instanceof Class<?> actualClass) {
                                if (needsObjectSchema(actualClass)) {
                                    nestedTypesToScan.add(actualClass);
                                }
                            }
                        }
                    } else if (needsObjectSchema(fieldRawType)) {
                        nestedTypesToScan.add(fieldRawType);
                    }
                }
            }

            PacketSchema schema =
                    new PacketSchema(
                            packetClass,
                            noArgsConstructor,
                            Collections.unmodifiableList(fieldSchemas));

            packetSchemaRegistry.put(type, schema);
            packetRegistry.put(type, packetClass);
            long endTime = System.nanoTime();
            ChirpLogger.debug(
                    "Successfully registered PacketSchema for "
                            + packetClass.getName()
                            + " in "
                            + (endTime - startTime) / 1_000_000.0
                            + "ms.");

            for (Class<?> nestedType : nestedTypesToScan) {
                registerObjectSchema(nestedType);
            }

        } catch (NoSuchMethodException e) {
            ChirpLogger.severe(
                    "Failed to register packet schema for "
                            + packetClass.getName()
                            + ": No no-argument constructor found. "
                            + e.getMessage());
            throw new IllegalArgumentException(
                    "Packet class "
                            + packetClass.getName()
                            + " must have a no-argument constructor to be registered as a"
                            + " ChirpPacket.",
                    e);
        } catch (SecurityException e) {
            ChirpLogger.severe(
                    "Security exception registering packet schema for "
                            + packetClass.getName()
                            + ": "
                            + e.getMessage());
            throw new RuntimeException(
                    "Security exception while accessing constructor/fields for packet "
                            + packetClass.getName(),
                    e);
        }
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
                "Registered callback for packet "
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
                callback.getOnTimeout().run();
                toRemove.add(packetId);
            }
        }

        for (UUID packetId : toRemove) {
            callbackRegistry.remove(packetId);
        }
    }

    public void setupCallbackRemoverThread() {
        Thread callbackRemoverThread =
                new Thread(
                        () -> {
                            while (!Thread.currentThread().isInterrupted()) {
                                try {
                                    Thread.sleep(20L);
                                    removeExpiredCallbacks();
                                } catch (InterruptedException e) {
                                    ChirpLogger.severe(
                                            "Callback remover thread interrupted: "
                                                    + e.getMessage());
                                    Thread.currentThread().interrupt();
                                    break;
                                } catch (Exception e) {
                                    ChirpLogger.severe(
                                            "Error in callback remover thread: " + e.getMessage());
                                }
                            }
                        });

        callbackRemoverThread.setName("Chirp-CallbackRemover");
        callbackRemoverThread.setDaemon(true);
        callbackRemoverThread.start();
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
        long startTime = System.currentTimeMillis();
        Reflections reflections = new Reflections(packageName, Scanners.TypesAnnotated);
        ChirpLogger.info("Starting scan for package: " + packageName);

        long currentSegmentStart = System.nanoTime();
        for (Class<?> packetClass :
                reflections.getTypesAnnotatedWith(ChirpPacket.class).stream()
                        .filter(c -> !c.getPackage().getName().contains("shaded"))
                        .collect(Collectors.toList())) {
            ChirpPacket chirpPacketAnnotation = packetClass.getAnnotation(ChirpPacket.class);
            if (chirpPacketAnnotation != null && !chirpPacketAnnotation.scan()) continue;

            try {
                registerPacket(packetClass);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(
                        "Failed to register packet: "
                                + packetClass.getName()
                                + " - "
                                + e.getMessage(),
                        e);
            }
        }
        long packetScanTime = System.nanoTime() - currentSegmentStart;
        ChirpLogger.debug(
                "  Packet scanning phase completed in " + packetScanTime / 1_000_000.0 + "ms.");

        currentSegmentStart = System.nanoTime();
        for (Class<?> converterClass :
                reflections.getTypesAnnotatedWith(ChirpConverter.class).stream()
                        .filter(c -> !c.getPackage().getName().contains("shaded"))
                        .collect(Collectors.toList())) {

            ChirpConverter chirpConverterAnnotation =
                    converterClass.getAnnotation(ChirpConverter.class);
            if (chirpConverterAnnotation != null && !chirpConverterAnnotation.scan()) continue;

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
                                + e.getMessage(),
                        e);
            }
        }
        long converterScanTime = System.nanoTime() - currentSegmentStart;
        ChirpLogger.debug(
                "  Converter scanning phase completed in "
                        + converterScanTime / 1_000_000.0
                        + "ms.");

        currentSegmentStart = System.nanoTime();
        for (Class<?> listenerClass :
                reflections.getTypesAnnotatedWith(ChirpListener.class).stream()
                        .filter(c -> !c.getPackage().getName().contains("shaded"))
                        .collect(Collectors.toList())) {

            ChirpListener chirpListenerAnnotation =
                    listenerClass.getAnnotation(ChirpListener.class);
            if (chirpListenerAnnotation != null && !chirpListenerAnnotation.scan()) continue;

            try {
                Constructor<?> ctor = listenerClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                Object listenerInstance = ctor.newInstance();
                registerListener(listenerInstance);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to register listener: "
                                + listenerClass.getName()
                                + " - "
                                + e.getMessage(),
                        e);
            }
        }
        long listenerScanTime = System.nanoTime() - currentSegmentStart;
        ChirpLogger.debug(
                "  Listener scanning phase completed in " + listenerScanTime / 1_000_000.0 + "ms.");

        long endTime = System.currentTimeMillis();
        ChirpLogger.info(
                "Overall scan for package: "
                        + packageName
                        + " completed in "
                        + (endTime - startTime)
                        + "ms.");
    }

    public void cleanup() {
        packetRegistry.clear();
        listenerRegistry.clear();
        converterRegistry.clear();
        callbackRegistry.clear();
        packetSchemaRegistry.clear();
        objectSchemaRegistry.clear();
        inProgressSchemas.clear();
        ChirpLogger.debug("Cleared all registrations");
    }
}
