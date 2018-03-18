package io.fluxcapacitor.javaclient.eventsourcing;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface EventSourced {
    String domain() default "";
    int snapshotPeriod() default 0;
    boolean cached() default false;
}
