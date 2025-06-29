package io.fjsn.chirp.converter.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

import java.lang.reflect.Type;

@ChirpConverter
public class BooleanConverter implements FieldConverter<Boolean> {
    @Override
    public JsonElement serialize(Boolean value, Type type, ChirpRegistry registry) {
        if (value == null) {
            return null;
        }
        return new JsonPrimitive(value);
    }

    @Override
    public Boolean deserialize(JsonElement json, Type type, ChirpRegistry registry) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(
                    "Expected JSON boolean primitive for Boolean, got: "
                            + json.getClass().getSimpleName());
        }
        return json.getAsBoolean();
    }
}
