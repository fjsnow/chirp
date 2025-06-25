package io.fjsn.chirp.converter.impl;

import io.fjsn.chirp.annotation.ChirpConverter;
import io.fjsn.chirp.converter.FieldConverter;

@ChirpConverter
public class CharacterConverter implements FieldConverter<Character> {
    public String serialize(Character value) {
        return Character.toString(value);
    }

    public Character deserialize(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot deserialize a null or empty string to a Character");
        }
        return value.charAt(0);
    }
}
