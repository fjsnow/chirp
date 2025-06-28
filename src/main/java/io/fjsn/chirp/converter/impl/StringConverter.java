package io.fjsn.chirp.converter.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

import java.lang.reflect.Type;

@ChirpConverter
public class StringConverter implements FieldConverter<String> {
    @Override
    public JsonElement serialize(String value, Type type, ChirpRegistry registry) {
        if (value == null) {
            return null;
        }
        return new JsonPrimitive(value);
    }

    @Override
    public String deserialize(JsonElement json, Type type, ChirpRegistry registry) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Expected JSON string primitive for String, got: "
                            + json.getClass().getSimpleName());
        }
        return json.getAsString();
    }
}
