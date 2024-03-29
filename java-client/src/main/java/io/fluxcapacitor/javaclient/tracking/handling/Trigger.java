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
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be placed on a message handler parameter or message handler method. The handler is only invoked if the
 * trigger for the message matches the requirements configured in the annotation.
 * <p>
 * If present on a parameter, the message that triggered the handled message is injected into the parameter if the
 * parameter type is assignable from the trigger message type.
 * <p>
 * This is typically useful when handling results (using {@link HandleResult}) or errors (using {@link HandleError}). In
 * those handlers it is often useful to have access to the message that triggered the result or error.
 * <p>
 * Valid parameter types are types that can be assigned from the trigger message payload type, or {@link Message} or
 * {@link DeserializingMessage}. Using {@link #value()} it is possible to filter what trigger messages to listen for. If
 * {@link #value()} is left empty any trigger that matches the parameter is injected. Using {@link #messageType()} it is
 * possible to filter the {@link MessageType} of the trigger message.
 * <p>
 * It is also possible to only listen for messages that were triggered by a given consumer using {@link #consumer()}.
 */
@Documented
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Trigger {
    /**
     * Filter what trigger messages may be injected. Parameters are only injected if the trigger message payload type
     * can be assigned to any of the given classes.
     * <p>
     * If left empty, any trigger that matches the parameter is injected.
     */
    Class<?>[] value() default {};

    /**
     * Filter what trigger messages may be injected. Parameters are only injected if the trigger message type is
     * contained in the returned array, or if the array is left empty.
     */
    MessageType[] messageType() default {};

    /**
     * Filter on the name of the consumer that produced the handled message. If multiple values are given, the match is
     * made if any of the mentioned consumers produced the message.
     * <p>
     * This makes it easy to e.g. track results or errors produced by one or more consumers.
     */
    String[] consumer() default {};
}
