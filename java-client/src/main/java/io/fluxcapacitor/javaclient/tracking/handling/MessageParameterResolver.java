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

import io.fluxcapacitor.common.handling.TypedParameterResolver;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

/**
 * Resolves handler method parameters of type {@link Message}.
 * <p>
 * This resolver is useful when a handler method needs access to the raw {@link Message} object
 * associated with the invocation context.
 * <p>
 * Resolution is attempted via the message context itself (if it implements {@link HasMessage}),
 * or from the current thread-local {@link DeserializingMessage} as a fallback.
 *
 * <p>Example:
 * <pre>{@code
 * @HandleCommand
 * public void handle(MyCommand command, Message message) {
 *     Instant timestamp = message.getTimestamp();
 * }
 * }</pre>
 */
public class MessageParameterResolver extends TypedParameterResolver<Object> {

    public MessageParameterResolver() {
        super(Message.class);
    }

    @Override
    public Function<Object, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return m -> m instanceof HasMessage hasMessage ? hasMessage.toMessage()
                : ofNullable(DeserializingMessage.getCurrent()).map(DeserializingMessage::toMessage).orElse(null);
    }
}