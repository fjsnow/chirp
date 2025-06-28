package io.fjsn.chirp.converter.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.converter.FieldConverter;
import io.fjsn.chirp.internal.serialization.PacketSerializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

public class OptionalConverter implements FieldConverter<Optional<?>> {

    @Override
    public JsonElement serialize(Optional<?> value, Type type, ChirpRegistry registry) {
        if (value == null || !value.isPresent()) {
            return JsonNull.INSTANCE;
        }

        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "Optional type must be parameterized (e.g., Optional<String>): "
                            + type.getTypeName());
        }

        ParameterizedType pt = (ParameterizedType) type;
        Type itemType = pt.getActualTypeArguments()[0];

        return PacketSerializer.serializeValue(value.get(), itemType, registry);
    }

    @Override
    public Optional<?> deserialize(JsonElement json, Type type, ChirpRegistry registry) {
        if (json == null || json.isJsonNull()) {
            return Optional.empty();
        }

        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                    "Optional type must be parameterized (e.g., Optional<String>): "
                            + type.getTypeName());
        }

        ParameterizedType pt = (ParameterizedType) type;
        Type itemType = pt.getActualTypeArguments()[0];

        Object deserializedValue = PacketSerializer.deserializeValue(json, itemType, registry);
        return Optional.ofNullable(deserializedValue);
    }
}
