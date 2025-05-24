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

import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.jvm.ReflectJvmMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Provides utilities for bridging Java reflection with Kotlin reflection.
 * <p>
 * This class enables Flux internals to interact with Kotlin symbols at runtime, such as retrieving
 * {@link kotlin.reflect.KFunction} or {@link kotlin.reflect.KParameter} instances from Java {@link Method}s,
 * {@link Constructor}s, and {@link Parameter}s.
 * <p>
 * It is used primarily to support nullability introspection and Kotlin-specific constructs in reflection-based logic.
 *
 * <p><b>Note:</b> This utility is internal and should not be considered part of the public API.
 */
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

    public static Class<?> convertIfKotlinClass(Object classObject) {
        return classObject instanceof KClass<?> k ? JvmClassMappingKt.getJavaClass(k) : null;
    }

}
