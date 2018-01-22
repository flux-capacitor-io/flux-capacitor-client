package io.fluxcapacitor.javaclient.tracking.handler;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HandleUsage {
}
