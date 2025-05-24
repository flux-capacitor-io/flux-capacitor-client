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

import lombok.AllArgsConstructor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

/**
 * Abstract base class for {@link ParameterResolver}s that resolve parameters based on type matching.
 * <p>
 * This class provides a default {@link #matches} implementation that checks whether the expected type of the parameter
 * is assignable from a configured base type.
 *
 * @param <M> the message or context type used for resolution (e.g. {@code HasMessage}, {@code Object}, etc.)
 */
@AllArgsConstructor
public abstract class TypedParameterResolver<M> implements ParameterResolver<M> {

    /**
     * The base type this resolver applies to.
     * Only parameters that are assignable from this type will be considered.
     */
    private final Class<?> type;

    /**
     * Determines whether the given parameter is eligible to be resolved by this resolver.
     * <p>
     * This default implementation matches if {@code parameter.getType()} is assignable from the configured base type.
     *
     * @param parameter        the method parameter to resolve
     * @param methodAnnotation the annotation on the method (if any)
     * @param value            the current message or context object
     * @return {@code true} if this resolver can resolve the parameter
     */
    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, M value) {
        return type.isAssignableFrom(parameter.getType());
    }
}
