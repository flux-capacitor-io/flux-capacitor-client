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

package io.fluxcapacitor.common.handling;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.function.Function;

/**
 * Mechanism to resolve method parameters in message handler methods (e.g. those annotated with {@code HandleEvent},
 * {@code HandleCommand}, etc.).
 * <p>
 * A {@code ParameterResolver} determines how to inject values into the parameters of a handler method based on the
 * message being handled, the parameter type, and other context. This allows custom logic to inject fields such as the
 * {@code payload}, {@code metadata}, authenticated {@code User}, or other derived values.
 *
 * <p>
 * Custom {@code ParameterResolver ParameterResolvers} can be registered with a {@code FluxCapacitorBuilder} to
 * influence handler invocation logic across one or more message types.
 *
 * @param <M> the type of message object used by the handler invocation context, often {@code DeserializingMessage}
 */
@FunctionalInterface
public interface ParameterResolver<M> {

    /**
     * Resolves a {@link Parameter} of a handler method into a value function based on the given message.
     * <p>
     * If the parameter cannot be resolved by this resolver and {@link #matches} is not implemented, this method must
     * return {@code null}.
     *
     * @param parameter        the parameter to resolve
     * @param methodAnnotation the annotation present on the handler method (e.g., {@code @HandleEvent})
     * @return a function that takes a message and returns a value to be injected into the method parameter, or
     * {@code null} if the parameter cannot be resolved and {@link #matches} is not implemented.
     */
    Function<M, Object> resolve(Parameter parameter, Annotation methodAnnotation);

    /**
     * Indicates whether the resolved value is compatible with the declared parameter type.
     * <p>
     * This method helps determine whether the parameter can be injected for a given message. It first invokes
     * {@link #resolve} and then verifies that the returned value (if any) is assignable to the parameter type.
     *
     * @param parameter        the parameter being checked
     * @param methodAnnotation the annotation on the handler method
     * @param value            the message instance to use for resolution
     * @return {@code true} if the parameter can be resolved and assigned to, {@code false} otherwise
     */
    default boolean matches(Parameter parameter, Annotation methodAnnotation, M value) {
        Function<M, Object> function = resolve(parameter, methodAnnotation);
        if (function == null) {
            return false;
        }
        Object parameterValue = function.apply(value);
        return parameterValue == null || parameter.getType().isAssignableFrom(parameterValue.getClass());
    }

    /**
     * Indicates whether this resolver contributes to determining handler method specificity when multiple handler
     * candidates are available.
     * <p>
     * If {@code true}, the resolver will participate in determining the most specific handler for a message. This is
     * relevant when the system must choose between overlapping handler signatures, e.g. for a resolver of a message's
     * payload.
     *
     * @return {@code true} if this resolver influences specificity decisions, {@code false} otherwise
     */
    default boolean determinesSpecificity() {
        return false;
    }

    /**
     * Determines whether a given message should be passed to a handler method based on this parameter's
     * characteristics.
     * <p>
     * This hook is used after {@link #matches} is invoked but before {@link #resolve} and can thus be used to prevent
     * other parameter resolvers from supplying a candidate for parameter injection.
     *
     * @param message   the message being evaluated
     * @param parameter the method parameter to test
     * @return {@code true} if the message should be processed, {@code false} if it should be filtered out
     */
    default boolean filterMessage(M message, Parameter parameter) {
        return true;
    }
}
