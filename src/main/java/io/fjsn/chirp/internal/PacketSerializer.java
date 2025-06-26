package io.fjsn.chirp.internal;

import com.google.gson.*;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.annotation.ChirpField;
import io.fjsn.chirp.converter.FieldConverter;

import java.lang.reflect.*;
import java.util.*;

public class PacketSerializer {

    public static JsonObject serialize(
            Object packet, String origin, boolean self, long sent, ChirpRegistry registry) {

        if (packet == null) throw new IllegalArgumentException("Packet cannot be null");

        JsonObject json = new JsonObject();
        String type =
                packet.getClass()
                        .getSimpleName()
                        .replaceAll("([a-z])([A-Z])", "$1_$2")
                        .toUpperCase();

        json.addProperty("type", type);
        json.addProperty("origin", origin);
        json.addProperty("self", self);
        json.addProperty("sent", sent);

        JsonObject data = new JsonObject();

        for (Field field : packet.getClass().getDeclaredFields()) {
            if (!field.isAnnotationPresent(ChirpField.class)) continue;

            field.setAccessible(true);

            try {
                Object value = field.get(packet);
                JsonElement element = serializeValue(value, field.getGenericType(), registry);
                data.add(field.getName(), element);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to access field: " + field.getName(), e);
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

        Class<?> packetClass = registry.getPacketRegistry().get(type);
        if (packetClass == null) {
            throw new IllegalArgumentException("Unknown packet type: " + type);
        }

        Object packet = packetClass.getDeclaredConstructor().newInstance();

        for (Field field : packetClass.getDeclaredFields()) {
            if (!field.isAnnotationPresent(ChirpField.class)) continue;

            field.setAccessible(true);
            String fieldName = field.getName();

            if (!data.has(fieldName) || data.get(fieldName).isJsonNull()) {
                if (!field.getType().isPrimitive()) {
                    field.set(packet, null);
                }
                continue;
            }

            JsonElement element = data.get(fieldName);
            Object value = deserializeValue(element, field.getGenericType(), registry);
            field.set(packet, value);
        }

        return packet;
    }

    public static Object fromJsonString(String jsonString, ChirpRegistry registry)
            throws ReflectiveOperationException {

        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        return deserialize(json, registry);
    }

    public static String toJsonString(
            Object packet, String origin, boolean self, long sent, ChirpRegistry registry) {

        JsonObject json = serialize(packet, origin, self, sent, registry);
        return json.toString();
    }

    private static JsonElement serializeValue(Object value, Type type, ChirpRegistry registry) {
        if (value == null) return JsonNull.INSTANCE;

        if (type instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();

            if (raw == List.class || raw == Set.class) {
                JsonArray array = new JsonArray();
                Type itemType = pt.getActualTypeArguments()[0];
                for (Object item : (Collection<?>) value) {
                    array.add(serializeValue(item, itemType, registry));
                }
                return array;
            }

            if (raw == Map.class) {
                JsonObject obj = new JsonObject();
                Type keyType = pt.getActualTypeArguments()[0];
                Type valueType = pt.getActualTypeArguments()[1];

                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    JsonElement key = serializeValue(entry.getKey(), keyType, registry);
                    JsonElement val = serializeValue(entry.getValue(), valueType, registry);
                    obj.add(key.getAsString(), val); // assumes keys are string-serializable
                }
                return obj;
            }
        }

        String typeKey = ChirpRegistry.normalizeTypeName(type);
        @SuppressWarnings("unchecked")
        FieldConverter<Object> converter =
                (FieldConverter<Object>) registry.getConverterRegistry().get(typeKey);

        if (converter != null) {
            return new JsonPrimitive(converter.serialize(value));
        }

        if (type instanceof Class<?> cls && !cls.isEnum() && !cls.isPrimitive() && !cls.isArray()) {
            JsonObject obj = new JsonObject();
            for (Field nestedField : cls.getDeclaredFields()) {
                if (!nestedField.isAnnotationPresent(ChirpField.class)) continue;
                nestedField.setAccessible(true);
                try {
                    Object nestedVal = nestedField.get(value);
                    obj.add(
                            nestedField.getName(),
                            serializeValue(nestedVal, nestedField.getGenericType(), registry));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(
                            "Failed to access nested field: " + nestedField.getName(), e);
                }
            }
            return obj;
        }

        throw new IllegalArgumentException(
                "No converter or strategy for type: " + type.getTypeName());
    }

    private static Object deserializeValue(JsonElement json, Type type, ChirpRegistry registry) {
        if (json == null || json.isJsonNull()) return null;

        if (type instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();

            if (raw == List.class) {
                List<Object> list = new ArrayList<>();
                Type itemType = pt.getActualTypeArguments()[0];
                for (JsonElement el : json.getAsJsonArray()) {
                    list.add(deserializeValue(el, itemType, registry));
                }
                return list;
            }

            if (raw == Set.class) {
                Set<Object> set = new HashSet<>();
                Type itemType = pt.getActualTypeArguments()[0];
                for (JsonElement el : json.getAsJsonArray()) {
                    set.add(deserializeValue(el, itemType, registry));
                }
                return set;
            }

            if (raw == Map.class) {
                Map<Object, Object> map = new HashMap<>();
                Type keyType = pt.getActualTypeArguments()[0];
                Type valType = pt.getActualTypeArguments()[1];
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                    Object key =
                            deserializeValue(new JsonPrimitive(entry.getKey()), keyType, registry);
                    Object val = deserializeValue(entry.getValue(), valType, registry);
                    map.put(key, val);
                }
                return map;
            }
        }

        String typeKey = ChirpRegistry.normalizeTypeName(type);
        FieldConverter<?> converter = registry.getConverterRegistry().get(typeKey);

        if (converter != null) {
            return converter.deserialize(json.getAsString());
        }

        if (type instanceof Class<?> cls && !cls.isEnum() && !cls.isPrimitive() && !cls.isArray()) {
            try {
                Object instance = cls.getDeclaredConstructor().newInstance();
                JsonObject obj = json.getAsJsonObject();
                for (Field nestedField : cls.getDeclaredFields()) {
                    if (!nestedField.isAnnotationPresent(ChirpField.class)) continue;
                    nestedField.setAccessible(true);
                    if (obj.has(nestedField.getName())) {
                        JsonElement el = obj.get(nestedField.getName());
                        Object nestedVal =
                                deserializeValue(el, nestedField.getGenericType(), registry);
                        nestedField.set(instance, nestedVal);
                    }
                }
                return instance;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "Failed to deserialize nested object of type: " + cls.getName(), e);
            }
        }

        throw new IllegalArgumentException(
                "No converter or strategy for type: " + type.getTypeName());
    }
}
