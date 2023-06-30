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

package io.fluxcapacitor.common.reflection;

import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.jvm.ReflectJvmMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class KotlinReflectionUtils {

    public static KParameter asKotlinParameter(Parameter parameter) {
        var executable = parameter.getDeclaringExecutable();
        var paramIndex = ReflectionUtils.getParameterIndex(parameter);
        KFunction<?> kotlinFunction = asKotlinFunction(executable);
        if (kotlinFunction == null) {
            throw new IllegalStateException("Could not obtain Kotlin function for: " + executable);
        }
        return kotlinFunction.getParameters().stream().filter(p -> p.getKind() == KParameter.Kind.VALUE)
                .skip(paramIndex).findFirst().orElse(null);
    }

    public static KFunction<?> asKotlinFunction(Executable executable) {
        return executable instanceof Method
                ? ReflectJvmMapping.getKotlinFunction((Method) executable)
                : ReflectJvmMapping.getKotlinFunction((Constructor<?>) executable);
    }

}
