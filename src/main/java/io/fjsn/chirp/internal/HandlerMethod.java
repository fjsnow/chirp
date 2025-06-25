package io.fjsn.chirp.internal;

import java.lang.reflect.Method;

public class HandlerMethod {

    public final Method method;
    public final Class<?> expectedPacketClass;

    public HandlerMethod(Method method, Class<?> expectedPacketClass) {
        this.method = method;
        this.expectedPacketClass = expectedPacketClass;
    }
}
