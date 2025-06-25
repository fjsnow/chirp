package io.fjsn.chirp.converter.impl;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

@ChirpConverter
public class FloatConverter implements FieldConverter<Float> {
    public String serialize(Float value) {
        return String.valueOf(value);
    }

    public Float deserialize(String value) {
        return Float.parseFloat(value);
    }
}
