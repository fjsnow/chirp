package io.fjsn.chirp.internal;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

public class HandlerMethod {

    public final MethodHandle methodHandle;
    public final Class<?> expectedPacketClass;

    public HandlerMethod(Method method, Class<?> expectedPacketClass) {
        this.expectedPacketClass = expectedPacketClass;

        try {
            method.setAccessible(true);
            this.methodHandle = java.lang.invoke.MethodHandles.lookup().unreflect(method);
        } catch (IllegalAccessException e) {

            throw new RuntimeException("Failed to unreflect method: " + method.getName(), e);
        }
    }
}
