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

import io.fluxcapacitor.common.MessageType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation used to declare that an annotation marks a method as a message handler for a specific
 * {@link MessageType}.
 * <p>
 * This annotation is not applied to handler methods directly, but is used as a meta-annotation on other handler
 * annotations such as {@link HandleEvent}, {@link HandleCommand}, or {@link HandleQuery}. It defines the message
 * semantics and optional constraints on the payload types that can be handled.
 * </p>
 *
 * <p>
 * The {@code value()} element specifies the {@link MessageType} this handler annotation corresponds to (e.g., EVENT,
 * COMMAND, QUERY).
 * </p>
 *
 * <p>
 * The {@code allowedClasses()} element can be used to restrict which payload types are valid for handler methods
 * annotated with the derived annotation. This is particularly useful when a single handler method is intended to handle
 * multiple related message types (e.g., a common base class or interface).
 * </p>
 *
 * @see MessageType
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface HandleMessage {

    /**
     * The type of message this handler annotation is intended to process. This determines how the framework routes and
     * dispatches the message to handlers.
     *
     * @return the message type (e.g., EVENT, COMMAND, QUERY)
     */
    MessageType value();

    /**
     * Optional list of payload types that are allowed for handler methods using this annotation.
     * <p>
     * If empty (default), any payload type is accepted. If one or more classes are specified, the handler method will
     * only be considered for messages whose payload type is assignable to one of the specified classes.
     * </p>
     *
     * <p>
     * Note that handler methods may still filter the payload type of messages they handle using their method
     * parameters. This allows fine-grained control even when {@code allowedClasses} is left broad or empty.
     * </p>
     *
     * @return array of permitted payload classes for the handler. If empty, any payload type is accepted.
     */
    Class<?>[] allowedClasses() default {};
}
