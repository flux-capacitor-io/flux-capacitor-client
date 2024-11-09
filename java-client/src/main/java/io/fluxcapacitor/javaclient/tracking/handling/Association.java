/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.search.SearchInclude;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to associate a message to a stored Handler (typically annotated with {@link Stateful}). If a message
 * is associated with one or several Handlers, the Handlers will be fetched and the message will be applied on all
 * Handlers.
 * <p>
 * This annotation can be added to properties (fields and getters) of the Handler class, or on handler methods. If
 * placed on a property of the Handler, the name of the property will be used to associate the message, unless another
 * property name is specified using {@link #value()}. If the annotation is used on a handler method, {@link #value()} is
 * required to match on a property by that name in the message payload.
 *
 * @see Stateful
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SearchInclude
public @interface Association {
    /**
     * Returns names of properties in the message payload to associate with. If the annotation is placed on a property
     * of the Handler this may be left empty to associate using the name of the Handler property.
     */
    String[] value() default {};

    /**
     * Returns path of the property to match on in the handler state. A message is only associated with a stored Handler
     * if the associated value can be found in a stored Handler at the given path.
     * <p>
     * If this is left empty and the annotation is on a field or getter, the name of field is used to filter any
     * matches. If this is left empty and the annotation is on a handler method, any Handler containing the
     * associated value is matched, regardless of the path of the value in the matched Handler.
     */
    String path() default "";

    /**
     * Returns payload classes for which this association can be used. If this array is empty or the payload of a
     * message is assignable to any of these classes, AND the class is not excluded via {@link #excludedClasses()}, an
     * association with the message is attempted.
     */
    Class<?>[] includedClasses() default {};

    /**
     * Returns payload classes for which this association is active. If the payload of a message is assignable to any of
     * these classes an association with the message is NOT attempted.
     */
    Class<?>[] excludedClasses() default {};

    /**
     * Returns whether metadata properties of messages should be checked for possible association with a stored handler.
     */
    boolean excludeMetadata() default false;

    /**
     * Returns whether the message matched by this handler should always be applied to any stored handlers. All other
     * configuration in this annotation will be ignored. This setting only has an effect if it is used in an annotation
     * of a handler method. I.e. it has no effect the association is on a field or getter of the handler.
     */
    boolean always() default false;

}
