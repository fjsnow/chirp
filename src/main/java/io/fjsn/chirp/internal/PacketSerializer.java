package io.fjsn.chirp.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.annotation.ChirpField;
import io.fjsn.chirp.converter.FieldConverter;

import java.lang.reflect.Field;

public class PacketSerializer {

    private PacketSerializer() {
        throw new UnsupportedOperationException("PacketSerializer cannot be instantiated");
    }

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

        Field[] fields = packet.getClass().getDeclaredFields();
        for (Field field : fields) {
            if (!field.isAnnotationPresent(ChirpField.class)) continue;
            field.setAccessible(true);

            try {
                Object value = field.get(packet);

                if (value == null) {
                    data.add(field.getName(), null);
                    continue;
                }

                String typeKey = ChirpRegistry.normalizeTypeName(field.getType());

                @SuppressWarnings("unchecked")
                FieldConverter<Object> converter =
                        (FieldConverter<Object>) registry.getConverterRegistry().get(typeKey);
                if (converter == null) {
                    throw new IllegalArgumentException(
                            "No converter registered for field type: "
                                    + field.getType().getSimpleName());
                }

                String serializedValue = converter.serialize(value);
                data.addProperty(field.getName(), serializedValue);

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

        Field[] fields = packetClass.getDeclaredFields();
        for (Field field : fields) {
            if (!field.isAnnotationPresent(ChirpField.class)) continue;
            field.setAccessible(true);

            String fieldName = field.getName();

            if ((!data.has(fieldName) || data.get(fieldName).isJsonNull())
                    && !field.getType().isPrimitive()) {
                field.set(packet, null);
                continue;
            }

            String serializedValue = data.get(fieldName).getAsString();
            String typeKey = ChirpRegistry.normalizeTypeName(field.getType());

            FieldConverter<?> converter = registry.getConverterRegistry().get(typeKey);
            if (converter == null) {
                throw new IllegalArgumentException(
                        "No converter registered for field type: "
                                + field.getType().getSimpleName());
            }

            Object value = converter.deserialize(serializedValue);
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
}
