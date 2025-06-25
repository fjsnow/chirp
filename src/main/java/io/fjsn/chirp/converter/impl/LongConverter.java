package io.fjsn.chirp.converter.impl;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

@ChirpConverter
public class LongConverter implements FieldConverter<Long> {
    public String serialize(Long value) {
        return String.valueOf(value);
    }

    public Long deserialize(String value) {
        return Long.parseLong(value);
    }
}
