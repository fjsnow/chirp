package io.fjsn.chirp.internal.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.internal.schema.FieldSchema;
import io.fjsn.chirp.internal.schema.ObjectSchema;
import io.fjsn.chirp.internal.schema.PacketSchema;
import io.fjsn.chirp.internal.util.ChirpLogger;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.UUID;

public class PacketSerializer {

    public static JsonObject serialize(
            Object packet,
            UUID packetId,
            String origin,
            boolean responding,
            UUID respondingTo,
            boolean self,
            long sent,
            ChirpRegistry registry) {

        if (packet == null) throw new IllegalArgumentException("Packet cannot be null");

        JsonObject json = new JsonObject();
        Class<?> packetClass = packet.getClass();
        String type =
                packetClass.getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();

        json.addProperty("packetId", packetId.toString());
        json.addProperty("type", type);
        json.addProperty("origin", origin);
        json.addProperty("responding", responding);
        if (responding) json.addProperty("respondingTo", respondingTo.toString());
        json.addProperty("self", self);
        json.addProperty("sent", sent);

        JsonObject data = new JsonObject();

        PacketSchema schema = registry.getPacketSchemaRegistry().get(type);
        if (schema == null) {
            throw new IllegalStateException(
                    "Packet schema not found for type: "
                            + type
                            + ". Ensure it's registered during Chirp initialization.");
        }

        for (FieldSchema fieldSchema : schema.fields) {
            try {
                Object value = fieldSchema.field.get(packet);
                JsonElement element = serializeValue(value, fieldSchema.genericType, registry);
                data.add(fieldSchema.fieldName, element);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                        "Failed to access field '"
                                + fieldSchema.fieldName
                                + "' during serialization using schema: "
                                + e.getMessage(),
                        e);
            }
        }

        json.add("data", data);
        return json;
    }

    public static Object deserialize(JsonObject json, ChirpRegistry registry)
            throws ReflectiveOperationException {

        if (json == null) throw new IllegalArgumentException("JsonObject cannot be null");

        String type = json.get("type").getAsString().toUpperCase();
        JsonObject data = json.getAsJsonObject("data");

        PacketSchema schema = registry.getPacketSchemaRegistry().get(type);
        if (schema == null) {
            throw new IllegalStateException(
                    "Unknown packet type or schema not found for: "
                            + type
                            + ". Ensure it's registered during Chirp initialization.");
        }

        Object packet = schema.noArgsConstructor.newInstance();

        for (FieldSchema fieldSchema : schema.fields) {
            String fieldName = fieldSchema.fieldName;

            if (!data.has(fieldName) || data.get(fieldName).isJsonNull()) {
                if (!fieldSchema.rawType.isPrimitive()) {
                    fieldSchema.field.set(packet, null);
                }
                continue;
            }

            JsonElement element = data.get(fieldName);
            Object value = deserializeValue(element, fieldSchema.genericType, registry);
            fieldSchema.field.set(packet, value);
        }

        return packet;
    }

    public static Object fromJsonString(String jsonString, ChirpRegistry registry)
            throws ReflectiveOperationException {
        long startTime = System.nanoTime();
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        Object deserialized = deserialize(json, registry);
        long endTime = System.nanoTime();
        ChirpLogger.debug(
                "Deserialized JSON string to object in "
                        + (endTime - startTime) / 1_000_000.0
                        + "ms.");
        return deserialized;
    }

    public static String toJsonString(
            Object packet,
            UUID packetId,
            String origin,
            boolean responding,
            UUID respondingTo,
            boolean self,
            long sent,
            ChirpRegistry registry) {
        long startTime = System.nanoTime();
        JsonObject json =
                serialize(packet, packetId, origin, responding, respondingTo, self, sent, registry);
        String jsonString = json.toString();
        long endTime = System.nanoTime();
        ChirpLogger.debug(
                "Serialized object to JSON string in "
                        + (endTime - startTime) / 1_000_000.0
                        + "ms.");
        return jsonString;
    }

    public static String toPrettyJsonString(
            Object packet,
            UUID packetId,
            String origin,
            boolean responding,
            UUID respondingTo,
            boolean self,
            long sent,
            ChirpRegistry registry) {

        long startTime = System.nanoTime();
        JsonObject json =
                serialize(packet, packetId, origin, responding, respondingTo, self, sent, registry);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String prettyJson = gson.toJson(json);
        long endTime = System.nanoTime();
        ChirpLogger.debug(
                "Serialized object to pretty JSON string in "
                        + (endTime - startTime) / 1_000_000.0
                        + "ms.");
        return prettyJson;
    }

    public static JsonElement serializeValue(Object value, Type type, ChirpRegistry registry) {
        if (value == null) return JsonNull.INSTANCE;

        String lookupKey;
        if (type instanceof ParameterizedType) {

            lookupKey = ChirpRegistry.normalizeTypeName(((ParameterizedType) type).getRawType());
        } else {

            lookupKey = ChirpRegistry.normalizeTypeName(type);
        }

        @SuppressWarnings("unchecked")
        FieldConverter<Object> converter =
                (FieldConverter<Object>) registry.getConverterRegistry().get(lookupKey);

        if (converter != null) {
            return converter.serialize(value, type, registry);
        }

        if (type instanceof Class<?> cls
                && !cls.isEnum()
                && !cls.isPrimitive()
                && !cls.isArray()
                && !cls.isInterface()) {
            ObjectSchema objectSchema = registry.getObjectSchemaRegistry().get(lookupKey);

            if (objectSchema == null) {
                throw new IllegalStateException(
                        "No pre-computed schema found for nested object type: "
                                + type.getTypeName()
                                + ". All custom @ChirpField types must have schemas registered"
                                + " during initialization or have a FieldConverter.");
            } else {
                JsonObject obj = new JsonObject();
                for (FieldSchema fieldSchema : objectSchema.fields) {
                    try {
                        Object nestedVal = fieldSchema.field.get(value);
                        obj.add(
                                fieldSchema.fieldName,
                                serializeValue(nestedVal, fieldSchema.genericType, registry));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(
                                "Failed to access nested field '"
                                        + fieldSchema.fieldName
                                        + "' during serialization using schema: "
                                        + e.getMessage(),
                                e);
                    }
                }
                return obj;
            }
        }

        throw new IllegalArgumentException(
                "No converter or strategy for type: "
                        + type.getTypeName()
                        + " (for value: "
                        + value
                        + ")");
    }

    public static Object deserializeValue(JsonElement json, Type type, ChirpRegistry registry) {
        if (json == null || json.isJsonNull()) return null;

        String lookupKey;
        if (type instanceof ParameterizedType) {
            lookupKey = ChirpRegistry.normalizeTypeName(((ParameterizedType) type).getRawType());
        } else {
            lookupKey = ChirpRegistry.normalizeTypeName(type);
        }

        FieldConverter<?> converter = registry.getConverterRegistry().get(lookupKey);

        if (converter != null) {
            return converter.deserialize(json, type, registry);
        }

        if (type instanceof Class<?> cls
                && !cls.isEnum()
                && !cls.isPrimitive()
                && !cls.isArray()
                && !cls.isInterface()) {
            ObjectSchema objectSchema = registry.getObjectSchemaRegistry().get(lookupKey);

            if (objectSchema == null) {
                throw new IllegalStateException(
                        "No pre-computed schema found for nested object type: "
                                + type.getTypeName()
                                + ". All custom @ChirpField types must have schemas registered"
                                + " during initialization or have a FieldConverter.");
            } else {
                try {
                    Object instance = objectSchema.noArgsConstructor.newInstance();
                    JsonObject obj = json.getAsJsonObject();
                    for (FieldSchema fieldSchema : objectSchema.fields) {
                        if (obj.has(fieldSchema.fieldName)) {
                            JsonElement el = obj.get(fieldSchema.fieldName);
                            Object nestedVal =
                                    deserializeValue(el, fieldSchema.genericType, registry);
                            fieldSchema.field.set(instance, nestedVal);
                        }
                    }
                    return instance;
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(
                            "Failed to deserialize nested object of type: "
                                    + cls.getName()
                                    + " using schema: "
                                    + e.getMessage(),
                            e);
                }
            }
        }

        throw new IllegalArgumentException(
                "No converter or strategy for type: "
                        + type.getTypeName()
                        + " (for JSON: "
                        + json
                        + ")");
    }
}
