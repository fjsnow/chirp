package io.fjsn.chirp.internal.schema;

import io.fjsn.chirp.ChirpRegistry; // To access normalizeTypeName
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

/**
 * Responsible for generating and managing {@link PacketSchema} and {@link ObjectSchema} instances.
 * This class handles the reflection-based scanning of packet and object fields to build their
 * serialization/deserialization schemas. It also manages the dynamic registration of {@link
 * EnumConverter} instances for encountered enum types.
 */
public class SchemaGenerator {

    private final Map<String, FieldConverter<?>> converterRegistry;
    private final Map<String, PacketSchema> packetSchemaRegistry;
    private final Map<String, ObjectSchema> objectSchemaRegistry;

    // Used to prevent infinite recursion during schema generation for cyclical dependencies
    private final ConcurrentHashMap<String, Boolean> inProgressSchemas;

    /**
     * Constructs a new SchemaGenerator.
     *
     * @param converterRegistry The main converter registry from ChirpRegistry. This is used to
     *     check for existing converters and to register new EnumConverters.
     * @param packetSchemaRegistry The main packet schema registry from ChirpRegistry.
     * @param objectSchemaRegistry The main object schema registry from ChirpRegistry.
     * @param inProgressSchemas A concurrent map used to track schemas currently being generated.
     */
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

    /**
     * Determines if a given class needs an {@link ObjectSchema}. Objects that are primitives,
     * arrays, interfaces, standard Java library types (java.lang.*), or enums (which are handled by
     * {@link EnumConverter}) do not require an explicit ObjectSchema. Additionally, if a custom
     * {@link FieldConverter} is already registered for the type, it doesn't need an ObjectSchema.
     *
     * @param clazz The class to check.
     * @return true if the class requires an ObjectSchema, false otherwise.
     */
    private boolean needsObjectSchema(Class<?> clazz) {
        if (clazz == null
                || clazz.isPrimitive()
                || clazz.isArray()
                || clazz.isInterface()
                || clazz.getName().startsWith("java.lang")
                || clazz.isEnum()) {
            return false;
        }
        // If a custom converter is already registered, we don't need a schema
        return !converterRegistry.containsKey(ChirpRegistry.normalizeTypeName(clazz));
    }

    /**
     * Registers an {@link ObjectSchema} for a given class if it doesn't already exist and is deemed
     * necessary by {@link #needsObjectSchema(Class)}. This method recursively registers schemas for
     * nested custom objects found in fields annotated with {@link ChirpField}. It ensures that a
     * no-argument constructor exists for the object.
     *
     * @param objectClass The class for which to register the ObjectSchema.
     * @throws IllegalArgumentException if the objectClass does not have a no-argument constructor.
     * @throws RuntimeException if there's a security exception or other reflection error during
     *     schema generation.
     */
    public void registerObjectSchema(Class<?> objectClass) {
        long startTime = System.nanoTime();

        // Handle enums by ensuring an EnumConverter is registered for their type
        if (objectClass.isEnum()) {
            String enumTypeKey = ChirpRegistry.normalizeTypeName(objectClass);
            if (!converterRegistry.containsKey(enumTypeKey)) {
                ChirpLogger.debug(
                        "SchemaGenerator: Registering EnumConverter for top-level enum class: "
                                + objectClass.getName());
                @SuppressWarnings({"unchecked", "rawtypes"})
                FieldConverter<?> enumConverter = new EnumConverter();
                converterRegistry.put(enumTypeKey, enumConverter);
            }
            return;
        }

        if (!needsObjectSchema(objectClass)) {
            return; // No schema needed for this type
        }

        String typeKey = ChirpRegistry.normalizeTypeName(objectClass);

        // Prevent redundant registrations and infinite recursion for cyclical dependencies
        if (objectSchemaRegistry.containsKey(typeKey)) {
            return;
        }

        // Use inProgressSchemas to handle circular dependencies during generation
        if (inProgressSchemas.putIfAbsent(typeKey, Boolean.TRUE) != null) {
            // Already in progress on another thread or circular reference detected during this path
            return;
        }

        try {
            // Ensure a no-argument constructor exists for instantiation
            Constructor<?> noArgsConstructor = objectClass.getDeclaredConstructor();
            noArgsConstructor.setAccessible(true); // Allow access to private/protected constructors

            List<FieldSchema> fieldSchemas = new ArrayList<>();
            Set<Class<?>> nestedTypesToScan = new HashSet<>();

            // Iterate over fields to build the schema
            for (Field field : objectClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(ChirpField.class)) {
                    FieldSchema fs = new FieldSchema(field);
                    fieldSchemas.add(fs);

                    Class<?> fieldRawType = fs.rawType;
                    String fieldRawTypeName = ChirpRegistry.normalizeTypeName(fieldRawType);

                    // If the field is an enum, ensure an EnumConverter is registered
                    if (fieldRawType.isEnum()) {
                        if (!converterRegistry.containsKey(fieldRawTypeName)) {
                            ChirpLogger.debug(
                                    "SchemaGenerator: Registering EnumConverter for field enum: "
                                            + fieldRawType.getName());
                            @SuppressWarnings({"unchecked", "rawtypes"})
                            FieldConverter<?> enumConverter = new EnumConverter();
                            converterRegistry.put(fieldRawTypeName, enumConverter);
                        }
                        continue;
                    }

                    // For parameterized types (e.g., List<MyObject>), scan the actual type
                    // arguments
                    if (fs.genericType instanceof ParameterizedType pt) {
                        for (Type argType : pt.getActualTypeArguments()) {
                            if (argType instanceof Class<?> actualClass) {
                                if (needsObjectSchema(actualClass)) {
                                    nestedTypesToScan.add(actualClass);
                                }
                            }
                        }
                    } else if (needsObjectSchema(fieldRawType)) {
                        // If it's a regular object field that needs a schema, add for recursive
                        // scan
                        nestedTypesToScan.add(fieldRawType);
                    }
                }
            }

            // Create and register the ObjectSchema
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

            // Recursively register schemas for nested custom types
            for (Class<?> nestedType : nestedTypesToScan) {
                registerObjectSchema(nestedType);
            }

        } catch (NoSuchMethodException e) {
            inProgressSchemas.remove(typeKey); // Clean up in-progress marker on failure
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
            inProgressSchemas.remove(typeKey); // Clean up in-progress marker on failure
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
            inProgressSchemas.remove(typeKey); // Ensure cleanup even on success
        }
    }

    /**
     * Registers a {@link PacketSchema} for a given class. This method validates the packet class,
     * ensures it has a no-argument constructor, builds the schema by reflecting on its {@link
     * ChirpField} annotated fields, and recursively registers schemas for any nested custom objects
     * or enum converters.
     *
     * @param packetClass The class to register as a packet.
     * @throws IllegalArgumentException if the packetClass is null, not annotated with {@link
     *     ChirpPacket}, or lacks a no-argument constructor, or if the type is already registered.
     * @throws RuntimeException if a security exception occurs during reflection.
     */
    public void registerPacket(Class<?> packetClass) {
        long startTime = System.nanoTime();

        if (packetClass == null) {
            throw new IllegalArgumentException("Packet class cannot be null");
        }

        if (!packetClass.isAnnotationPresent(ChirpPacket.class)) {
            throw new IllegalArgumentException("Packet class must be annotated with @ChirpPacket");
        }

        // Generate a canonical type name for the packet
        String type =
                packetClass.getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();

        // Check for existing registrations
        if (packetSchemaRegistry.containsKey(type)) {
            // This happens if the builder calls `packet` twice, or scan finds it twice.
            // It's benign if the schema is identical, but preventing re-registration avoids
            // potential issues if called in different contexts or with different classloaders.
            ChirpLogger.warning(
                    "Packet schema for type '" + type + "' is already registered. Skipping.");
            return;
        }

        try {
            // Ensure a no-argument constructor for packet instantiation
            Constructor<?> noArgsConstructor = packetClass.getDeclaredConstructor();
            noArgsConstructor.setAccessible(true); // Allow access to private/protected constructors

            List<FieldSchema> fieldSchemas = new ArrayList<>();
            Set<Class<?>> nestedTypesToScan = new HashSet<>();

            // Iterate over fields to build the packet schema
            for (Field field : packetClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(ChirpField.class)) {
                    FieldSchema fs = new FieldSchema(field);
                    fieldSchemas.add(fs);

                    Class<?> fieldRawType = fs.rawType;
                    String fieldRawTypeName = ChirpRegistry.normalizeTypeName(fieldRawType);

                    // If the field is an enum, ensure an EnumConverter is registered
                    if (fieldRawType.isEnum()) {
                        if (!converterRegistry.containsKey(fieldRawTypeName)) {
                            ChirpLogger.debug(
                                    "SchemaGenerator: Registering EnumConverter for packet field"
                                            + " enum: "
                                            + fieldRawType.getName());
                            @SuppressWarnings({"unchecked", "rawtypes"})
                            FieldConverter<?> enumConverter = new EnumConverter();
                            converterRegistry.put(fieldRawTypeName, enumConverter);
                        }
                        continue;
                    }

                    // For parameterized types (e.g., List<MyObject>), scan the actual type
                    // arguments
                    if (fs.genericType instanceof ParameterizedType pt) {
                        for (Type argType : pt.getActualTypeArguments()) {
                            if (argType instanceof Class<?> actualClass) {
                                if (needsObjectSchema(actualClass)) {
                                    nestedTypesToScan.add(actualClass);
                                }
                            }
                        }
                    } else if (needsObjectSchema(fieldRawType)) {
                        // If it's a regular object field that needs a schema, add for recursive
                        // scan
                        nestedTypesToScan.add(fieldRawType);
                    }
                }
            }

            // Create and register the PacketSchema
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

            // Recursively register schemas for any nested custom objects
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

    /** Clears all registered packet and object schemas, and the in-progress schema tracking map. */
    public void cleanup() {
        packetSchemaRegistry.clear();
        objectSchemaRegistry.clear();
        inProgressSchemas.clear();
        ChirpLogger.debug("SchemaGenerator: Cleared all schemas.");
    }
}
