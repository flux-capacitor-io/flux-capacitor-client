package io.fluxcapacitor.javaclient.eventsourcing;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface ApplyEvent {
}
