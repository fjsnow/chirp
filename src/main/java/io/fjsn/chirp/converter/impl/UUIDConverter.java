package io.fjsn.chirp.converter.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

import java.lang.reflect.Type;
import java.util.UUID;

@ChirpConverter
public class UUIDConverter implements FieldConverter<UUID> {
    @Override
    public JsonElement serialize(UUID value, Type type, ChirpRegistry registry) {
        if (value == null) {
            return null;
        }
        return new JsonPrimitive(value.toString());
    }

    @Override
    public UUID deserialize(JsonElement json, Type type, ChirpRegistry registry) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Expected JSON string primitive for UUID, got: "
                            + json.getClass().getSimpleName());
        }
        return UUID.fromString(json.getAsString());
    }
}
