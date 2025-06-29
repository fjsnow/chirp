package io.fjsn.chirp.converter.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.internal.serialization.PacketSerializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ListConverter implements FieldConverter<List<?>> {

    @Override
    public JsonElement serialize(List<?> value, Type type, ChirpRegistry registry) {
        if (value == null) return null;

        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "List type must be parameterized (e.g., List<String>): " + type.getTypeName());
        }

        ParameterizedType pt = (ParameterizedType) type;
        Type itemType = pt.getActualTypeArguments()[0];

        JsonArray array = new JsonArray();
        for (Object item : value) {

            array.add(PacketSerializer.serializeValue(item, itemType, registry));
        }
        return array;
    }

    @Override
    public List<?> deserialize(JsonElement json, Type type, ChirpRegistry registry) {
        if (json == null || json.isJsonNull()) return null;
        if (!json.isJsonArray())
            throw new IllegalArgumentException(
                    "Expected JSON array for List, got: " + json.getClass().getSimpleName());

        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "List type must be parameterized (e.g., List<String>): " + type.getTypeName());
        }

        ParameterizedType pt = (ParameterizedType) type;
        Type itemType = pt.getActualTypeArguments()[0];

        List<Object> list = new ArrayList<>();
        for (JsonElement el : json.getAsJsonArray()) {

            list.add(PacketSerializer.deserializeValue(el, itemType, registry));
        }
        return list;
    }
}
