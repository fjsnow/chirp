package io.fjsn.chirp.converter.impl;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

@ChirpConverter
public class DoubleConverter implements FieldConverter<Double> {
    public String serialize(Double value) {
        return String.valueOf(value);
    }

    public Double deserialize(String value) {
        return Double.parseDouble(value);
    }
}
