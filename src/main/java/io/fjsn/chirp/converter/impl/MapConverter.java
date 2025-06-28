package io.fjsn.chirp.converter.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.internal.PacketSerializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class MapConverter implements FieldConverter<Map<?, ?>> {

    @Override
    public JsonElement serialize(Map<?, ?> value, Type type, ChirpRegistry registry) {
        if (value == null) return null;

        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "Map type must be parameterized (e.g., Map<String, Integer>): "
                            + type.getTypeName());
        }

        ParameterizedType pt = (ParameterizedType) type;
        Type keyType = pt.getActualTypeArguments()[0];
        Type valueType = pt.getActualTypeArguments()[1];

        JsonObject obj = new JsonObject();
        for (Map.Entry<?, ?> entry : value.entrySet()) {

            JsonElement keyElement =
                    PacketSerializer.serializeValue(entry.getKey(), keyType, registry);
            if (!keyElement.isJsonPrimitive() || !keyElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(
                        "Map keys must serialize to a JSON string primitive. Invalid key type or"
                            + " converter for: "
                                + keyType.getTypeName());
            }

            JsonElement valElement =
                    PacketSerializer.serializeValue(entry.getValue(), valueType, registry);
            obj.add(keyElement.getAsString(), valElement);
        }
        return obj;
    }

    @Override
    public Map<?, ?> deserialize(JsonElement json, Type type, ChirpRegistry registry) {
        if (json == null || json.isJsonNull()) return null;
        if (!json.isJsonObject())
            throw new IllegalArgumentException(
                    "Expected JSON object for Map, got: " + json.getClass().getSimpleName());

        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "Map type must be parameterized (e.g., Map<String, Integer>): "
                            + type.getTypeName());
        }

        ParameterizedType pt = (ParameterizedType) type;
        Type keyType = pt.getActualTypeArguments()[0];
        Type valueType = pt.getActualTypeArguments()[1];

        Map<Object, Object> map = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {

            Object key =
                    PacketSerializer.deserializeValue(
                            new JsonPrimitive(entry.getKey()), keyType, registry);
            Object val = PacketSerializer.deserializeValue(entry.getValue(), valueType, registry);
            map.put(key, val);
        }
        return map;
    }
}
