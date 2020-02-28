package io.fluxcapacitor.javaclient.scheduling;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation placed on the payload class of a Schedule handled by {@link io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule}
 * annotated methods. If this annotation is present the same payload will be rescheduled after handling using a given
 * delay in ms.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Periodic {
    /**
     * Returns the schedule delay in milliseconds. Must be positive.
     */
    long value();

    /**
     * Returns true if this periodic schedule should be automatically started if it's not already active. Defaults to
     * {@code true}.
     */
    boolean autoStart() default true;

    /**
     * Returns the initial schedule delay in milliseconds. Only relevant when {@link #autoStart()} is true.
     */
    long initialDelay() default 0;

    /**
     * Returns true if the schedule should continue after an error. Defaults to {@code true}.
     */
    boolean continueOnError() default true;

    /**
     * Returns the id of the periodic schedule. Defaults to the fully qualified name of the schedule class.
     */
    String scheduleId() default "";
}
