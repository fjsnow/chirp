package io.fjsn.chirp.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChirpListener {
    public boolean scan() default true;
}
