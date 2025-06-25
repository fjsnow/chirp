package io.fjsn.chirp.converter.impl;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

@ChirpConverter
public class ByteConverter implements FieldConverter<Byte> {
    public String serialize(Byte value) {
        return String.valueOf(value);
    }

    public Byte deserialize(String value) {
        return Byte.parseByte(value);
    }
}
