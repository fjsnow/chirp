package io.fjsn.chirp.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChirpPacket {
    public boolean scan() default true;
}
