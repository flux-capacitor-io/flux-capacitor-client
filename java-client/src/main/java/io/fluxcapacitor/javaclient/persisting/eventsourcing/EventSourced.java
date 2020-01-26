package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface EventSourced {
    String domain() default "";
    int snapshotPeriod() default 0;
    boolean cached() default false;
    boolean commitInBatch() default true;
}
