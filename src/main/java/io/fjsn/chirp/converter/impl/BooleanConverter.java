package io.fjsn.chirp.converter.impl;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

@ChirpConverter
public class BooleanConverter implements FieldConverter<Boolean> {
    public String serialize(Boolean value) {
        return Boolean.toString(value);
    }

    public Boolean deserialize(String value) {
        return Boolean.parseBoolean(value);
    }
}
