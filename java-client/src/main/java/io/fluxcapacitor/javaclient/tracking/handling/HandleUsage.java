package io.fluxcapacitor.javaclient.tracking.handling;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HandleUsage {
}
