package io.fjsn.chirp.converter.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.converter.FieldConverter;

import java.lang.reflect.Type;

public class EnumConverter implements FieldConverter<Enum<?>> {

    public EnumConverter() {}

    @Override
    public JsonElement serialize(Enum<?> value, Type type, ChirpRegistry registry) {
        if (value == null) {
            return null;
        }
        return new JsonPrimitive(value.name());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Enum<?> deserialize(JsonElement json, Type type, ChirpRegistry registry) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Expected JSON string primitive for Enum, got: "
                            + json.getClass().getSimpleName());
        }
        String enumName = json.getAsString();

        if (!(type instanceof Class) || !((Class<?>) type).isEnum()) {
            throw new IllegalArgumentException(
                    "Expected an Enum class for type, got: " + type.getTypeName());
        }
        Class<? extends Enum> enumClass = (Class<? extends Enum>) type;

        try {
            return Enum.valueOf(enumClass, enumName);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid enum name '" + enumName + "' for enum type " + enumClass.getName(), e);
        }
    }
}
