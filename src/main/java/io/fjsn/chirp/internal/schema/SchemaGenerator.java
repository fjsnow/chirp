package io.fjsn.chirp.internal.schema;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.annotation.ChirpField;
import io.fjsn.chirp.annotation.ChirpPacket;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.converter.impl.EnumConverter;
import io.fjsn.chirp.internal.util.ChirpLogger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SchemaGenerator {

    private final Map<String, FieldConverter<?>> converterRegistry;
    private final Map<String, PacketSchema> packetSchemaRegistry;
    private final Map<String, ObjectSchema> objectSchemaRegistry;

    private final ConcurrentHashMap<String, Boolean> inProgressSchemas;

    public SchemaGenerator(
            Map<String, FieldConverter<?>> converterRegistry,
            Map<String, PacketSchema> packetSchemaRegistry,
            Map<String, ObjectSchema> objectSchemaRegistry,
            ConcurrentHashMap<String, Boolean> inProgressSchemas) {
        this.converterRegistry = converterRegistry;
        this.packetSchemaRegistry = packetSchemaRegistry;
        this.objectSchemaRegistry = objectSchemaRegistry;
        this.inProgressSchemas = inProgressSchemas;
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

        return !converterRegistry.containsKey(ChirpRegistry.normalizeTypeName(clazz));
    }

    public void registerObjectSchema(Class<?> objectClass) {
        long startTime = System.nanoTime();

        if (objectClass.isEnum()) {
            String enumTypeKey = ChirpRegistry.normalizeTypeName(objectClass);
            if (!converterRegistry.containsKey(enumTypeKey)) {
                ChirpLogger.debug(
                        "SchemaGenerator: Registering EnumConverter for top-level enum class: "
                                + objectClass.getName());
                FieldConverter<?> enumConverter = new EnumConverter();
                converterRegistry.put(enumTypeKey, enumConverter);
            }
            return;
        }

        if (!needsObjectSchema(objectClass)) {
            return;
        }

        String typeKey = ChirpRegistry.normalizeTypeName(objectClass);

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
                    String fieldRawTypeName = ChirpRegistry.normalizeTypeName(fieldRawType);

                    if (fieldRawType.isEnum()) {
                        if (!converterRegistry.containsKey(fieldRawTypeName)) {
                            ChirpLogger.debug(
                                    "SchemaGenerator: Registering EnumConverter for field enum: "
                                            + fieldRawType.getName());
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
                    "SchemaGenerator: Successfully registered ObjectSchema for "
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
                    "SchemaGenerator: Failed to register schema for "
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
                    "SchemaGenerator: Failed to register schema for "
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

        if (packetSchemaRegistry.containsKey(type)) {
            ChirpLogger.warning(
                    "Packet schema for type '" + type + "' is already registered. Skipping.");
            return;
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
                    String fieldRawTypeName = ChirpRegistry.normalizeTypeName(fieldRawType);

                    if (fieldRawType.isEnum()) {
                        if (!converterRegistry.containsKey(fieldRawTypeName)) {
                            ChirpLogger.debug(
                                    "SchemaGenerator: Registering EnumConverter for packet field"
                                            + " enum: "
                                            + fieldRawType.getName());
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
            long endTime = System.nanoTime();
            ChirpLogger.debug(
                    "SchemaGenerator: Successfully registered PacketSchema for "
                            + packetClass.getName()
                            + " in "
                            + (endTime - startTime) / 1_000_000.0
                            + "ms.");

            for (Class<?> nestedType : nestedTypesToScan) {
                registerObjectSchema(nestedType);
            }

        } catch (NoSuchMethodException e) {
            ChirpLogger.severe(
                    "SchemaGenerator: Failed to register packet schema for "
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

    public void cleanup() {
        packetSchemaRegistry.clear();
        objectSchemaRegistry.clear();
        inProgressSchemas.clear();
        ChirpLogger.debug("SchemaGenerator: Cleared all schemas.");
    }
}
