package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be placed on methods in events. Allows the event to apply itself to an aggregate. The aggregate will
 * get injected into the method automatically when it is event sourced or when the event is applied.
 * <p>
 * Annotated methods may consist of any number of parameters. If any parameter type is assignable to the loaded
 * aggregate type it will be injected. If no parameter is assignable to the aggregate type it is assumed that a new
 * aggregate will be created. Other parameters like event {@link Message} will be automatically injected.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Apply {
}
