package io.fjsn.chirp.converter.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import io.fjsn.chirp.ChirpRegistry;
import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

import java.lang.reflect.Type;

@ChirpConverter
public class CharacterConverter implements FieldConverter<Character> {
    @Override
    public JsonElement serialize(Character value, Type type, ChirpRegistry registry) {
        if (value == null) {
            return null;
        }
        return new JsonPrimitive(String.valueOf(value));
    }

    @Override
    public Character deserialize(JsonElement json, Type type, ChirpRegistry registry) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Expected JSON string primitive for Character, got: "
                            + json.getClass().getSimpleName());
        }
        String value = json.getAsString();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Cannot deserialize an empty string to a Character");
        }
        return value.charAt(0);
    }
}
