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

package io.fluxcapacitor.javaclient.publishing.routing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to identify the routing key of a message, which in turn is used to compute the message segment using
 * consistent hashing. If the routing key and thus segment of two messages are equal, the messages are always given to a
 * consumer in order. Often, this is used to ensure that messages about the same subject (say one aggregate), aren't
 * handled in parallel to prevent concurrent modifications.
 * <p>
 * This annotation can be used as a field, method or type annotation in a payload class. If placed on a method, that
 * method must contain no parameters. If it is on a field or getter method it uses the property to obtain the routing
 * key. The value of the property will be converted to a string via the value's {@code toString()} method.
 * <p>
 * If the annotation is used at the class level of the payload class, a property name should be specified using
 * {@link #value()}. That property will first be looked up inside the message's
 * {@link io.fluxcapacitor.common.api.Metadata}. If it is not found there, the property will be looked for inside the
 * payload. In that case, the property name may also contain one or more slashes ('/') if the property is hiding inside
 * an embedded field.
 * <p>
 * Finally, it is also possible to place this annotation on a handler method. In that case, the property name specified
 * by {@link #value()} of the payload (metadata or payload property) will be used to compute the routing key before
 * handling. This effectively overrides any routing key computed upon publication of the message. <b>Important note:</b>
 * if the annotation is on a handler method, make sure the consumer configuration has <code>ignoreSegment =
 * true</code>; otherwise messages may be missed by the consumer.
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RoutingKey {

    /**
     * Returns the metadata key or property name to use to look up the routing key. If left empty, the annotated field
     * or method will be used to determine the routing key.
     */
    String value() default "";
}
