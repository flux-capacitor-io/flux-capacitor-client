package io.fluxcapacitor.javaclient.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@HandleWeb(value = "", method = HttpRequestMethod.UNLOCK)
public @interface HandleUnlock {
    String[] value() default {};
    boolean disabled() default false;
    boolean passive() default false;
}