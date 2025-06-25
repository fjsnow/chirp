package io.fjsn.chirp.converter;

public interface FieldConverter<T> {
    String serialize(T value);

    T deserialize(String value);
}
