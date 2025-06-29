package io.fjsn.chirp.internal.schema;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

public class FieldSchema {
    public final Field field;
    public final String fieldName;
    public final Type genericType;
    public final Class<?> rawType;

    public FieldSchema(Field field) {
        this.field = field;
        this.fieldName = field.getName();
        this.genericType = field.getGenericType();
        this.rawType = field.getType();
        field.setAccessible(true);
    }
}
