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

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.function.Function;

/**
 * Resolves handler method parameters by injecting the message payload.
 *
 * <p>This resolver matches a parameter when its declared type is assignable from the actual payload class
 * of the incoming message.
 *
 * <p>This resolver is typically used in conjunction with filters such as {@link PayloadFilter} to determine
 * handler compatibility based on payload types.
 *
 * <p>Special care is taken to allow null payloads for parameters that are declared nullable,
 * which can occur during upcasting or transformation pipelines.
 *
 * @see HasMessage#getPayload()
 * @see HasMessage#getPayloadClass()
 * @see ParameterResolver
 */
public class PayloadParameterResolver implements ParameterResolver<HasMessage> {
    @Override
    public boolean matches(Parameter p, Annotation methodAnnotation, HasMessage value) {
        return p.getType().isAssignableFrom(value.getPayloadClass());
    }

    @Override
    public Function<HasMessage, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return HasMessage::getPayload;
    }

    @Override
    public boolean filterMessage(HasMessage message, Parameter parameter) {
        return message.getPayload() != null || ReflectionUtils.isNullable(parameter); //may be the case after upcasting
    }

    /**
     * Indicates that this resolver contributes to disambiguating handler methods when multiple handlers are present in
     * the same target class.
     *
     * <p>This is useful when more than one method matches a message, and the framework must
     * decide which method is more specific. If this returns {@code true}, the resolver's presence and compatibility
     * with the parameter may influence which handler is selected.
     *
     * @return true, signaling that this resolver helps determine method specificity
     */
    @Override
    public boolean determinesSpecificity() {
        return true;
    }
}
