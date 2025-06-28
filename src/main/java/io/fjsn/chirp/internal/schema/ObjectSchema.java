package io.fjsn.chirp.internal.schema;

import java.lang.reflect.Constructor;
import java.util.List;

public class ObjectSchema {
    public final Class<?> objectClass;
    public final Constructor<?> noArgsConstructor;
    public final List<FieldSchema> fields;

    public ObjectSchema(
            Class<?> objectClass, Constructor<?> noArgsConstructor, List<FieldSchema> fields) {
        this.objectClass = objectClass;
        this.noArgsConstructor = noArgsConstructor;
        this.fields = fields;
    }
}
