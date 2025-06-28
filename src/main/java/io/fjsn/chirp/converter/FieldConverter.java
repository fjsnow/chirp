package io.fjsn.chirp.converter;

import com.google.gson.JsonElement;

import io.fjsn.chirp.ChirpRegistry;

import java.lang.reflect.Type;

public interface FieldConverter<T> {
    JsonElement serialize(T value, Type type, ChirpRegistry registry);

    T deserialize(JsonElement json, Type type, ChirpRegistry registry);
}
