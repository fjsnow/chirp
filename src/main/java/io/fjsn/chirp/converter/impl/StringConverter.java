package io.fjsn.chirp.converter.impl;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

@ChirpConverter
public class StringConverter implements FieldConverter<String> {
    public String serialize(String value) {
        return value;
    }

    public String deserialize(String value) {
        return value;
    }
}
