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
 * Declares how a message should be routed to a stateful handler instance (typically annotated with {@link Stateful}).
 * <p>
 * {@code @Association} enables matching of incoming messages with persisted handler instances. Matching handlers are
 * automatically loaded and the message is applied to them.
 *
 * <h2>Usage</h2>
 * This annotation can be placed on:
 * <ul>
 *     <li>A <strong>field or getter</strong> in a stateful handler — this declares which part of the handler's state
 *     is used for association.</li>
 *     <li>A <strong>handler method</strong> — this declares which message field(s) should be matched against
 *     handler state.</li>
 * </ul>
 *
 * <h2>Matching Semantics</h2>
 * A message is associated with a handler if:
 * <ul>
 *     <li>The value of the property in the message equals the value in the handler's state, and</li>
 *     <li>The name of the message property matches the name (or explicitly declared {@link #value()}) of the handler field.</li>
 * </ul>
 * This dual condition prevents false positives when different fields share similar values.
 *
 * <h2>Multiple Handler Matches</h2>
 * A single message may match multiple handlers. All matching handlers will be loaded and the message will be applied to each.
 *
 * <h3>Example: Associating by field</h3>
 * <pre>{@code
 * @Value
 * @Stateful
 * public class PaymentProcess {
 *     @EntityId String id;
 *
 *     @Association
 *     String pspReference;
 * }
 * }</pre>
 * In this example, any message with a `pspReference` field matching the handler’s field will be routed to it.
 *
 * <h3>Example: Method-level association</h3>
 * <pre>{@code
 * @HandleEvent
 * @Association("userId")
 * void on(UserDeleted event) {
 *     ...
 * }
 * }</pre>
 * Associates based on the `userId` field in the message payload.
 *
 * <h2>Advanced Configuration</h2>
 * <ul>
 *     <li>{@link #path()} can be used to match nested or computed properties in the handler state.</li>
 *     <li>{@link #includedClasses()} and {@link #excludedClasses()} can restrict association to specific message types.</li>
 *     <li>{@link #excludeMetadata()} disables metadata-based matching.</li>
 *     <li>{@link #always()} applies the message to <em>all</em> persisted handlers regardless of association (use with care).</li>
 * </ul>
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
     * matches. If this is left empty and the annotation is on a handler method, any Handler containing the associated
     * value is matched, regardless of the path of the value in the matched Handler.
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
     * Returns whether metadata properties of messages should be checked for possible association with a stored
     * handler.
     */
    boolean excludeMetadata() default false;

    /**
     * Returns whether the message matched by this handler should always be applied to any stored handlers. All other
     * configuration in this annotation will be ignored. This setting only has an effect if it is used in an annotation
     * of a handler method. I.e. it has no effect if the association is on a field or getter of the handler.
     * <p>
     * Note: be very careful using this when there are many stored handlers, as each handler will be fetched and
     * updated. In that case it is prudent to look for alternatives.
     */
    boolean always() default false;

}
