package io.fjsn.chirp.converter.impl;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

import java.util.UUID;

@ChirpConverter
public class UUIDConverter implements FieldConverter<UUID> {
    public String serialize(UUID value) {
        return value.toString();
    }

    public UUID deserialize(String value) {
        return UUID.fromString(value);
    }
}
