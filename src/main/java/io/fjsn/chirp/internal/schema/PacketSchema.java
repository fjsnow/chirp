package io.fjsn.chirp.internal.schema;

import java.lang.reflect.Constructor;
import java.util.List;

public class PacketSchema {
    public final Class<?> packetClass;
    public final Constructor<?> noArgsConstructor;
    public final List<FieldSchema> fields;

    public PacketSchema(
            Class<?> packetClass, Constructor<?> noArgsConstructor, List<FieldSchema> fields) {
        this.packetClass = packetClass;
        this.noArgsConstructor = noArgsConstructor;
        this.fields = fields;
    }
}
