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

@FunctionalInterface
public interface ParameterResolver<M> {

    Function<M, Object> resolve(Parameter parameter, Annotation methodAnnotation);

    default boolean matches(Parameter parameter, Annotation methodAnnotation, M value) {
        Function<M, Object> function = resolve(parameter, methodAnnotation);
        if (function == null) {
            return false;
        }
        Object parameterValue = function.apply(value);
        return parameterValue == null || parameter.getType().isAssignableFrom(parameterValue.getClass());
    }

    default boolean determinesSpecificity() {
        return false;
    }

    default boolean filterMessage(M message, Parameter parameter) {
        return true;
    }

}
