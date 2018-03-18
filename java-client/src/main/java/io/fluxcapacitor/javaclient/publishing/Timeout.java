package io.fluxcapacitor.javaclient.publishing;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Timeout {
    int millis();
}
