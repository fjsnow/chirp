package io.fjsn.chirp.converter.impl;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

@ChirpConverter
public class IntegerConverter implements FieldConverter<Integer> {
    public String serialize(Integer value) {
        return String.valueOf(value);
    }

    public Integer deserialize(String value) {
        return Integer.parseInt(value);
    }
}
