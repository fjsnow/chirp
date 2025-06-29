package io.fjsn.chirp.converter.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.internal.serialization.PacketSerializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

public class SetConverter implements FieldConverter<Set<?>> {

    @Override
    public JsonElement serialize(Set<?> value, Type type, ChirpRegistry registry) {
        if (value == null) return null;

        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "Set type must be parameterized (e.g., Set<String>): " + type.getTypeName());
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
    public Set<?> deserialize(JsonElement json, Type type, ChirpRegistry registry) {
        if (json == null || json.isJsonNull()) return null;
        if (!json.isJsonArray())
            throw new IllegalArgumentException(
                    "Expected JSON array for Set, got: " + json.getClass().getSimpleName());

        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "Set type must be parameterized (e.g., Set<String>): " + type.getTypeName());
        }

        ParameterizedType pt = (ParameterizedType) type;
        Type itemType = pt.getActualTypeArguments()[0];

        Set<Object> set = new HashSet<>();
        for (JsonElement el : json.getAsJsonArray()) {

            set.add(PacketSerializer.deserializeValue(el, itemType, registry));
        }
        return set;
    }
}
