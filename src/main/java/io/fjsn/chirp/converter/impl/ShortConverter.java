package io.fjsn.chirp.converter.impl;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

@ChirpConverter
public class ShortConverter implements FieldConverter<Short> {
    public String serialize(Short value) {
        return String.valueOf(value);
    }

    public Short deserialize(String value) {
        return Short.parseShort(value);
    }
}
