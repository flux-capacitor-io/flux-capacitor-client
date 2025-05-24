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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.lang.model.element.ExecutableElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

/**
 * Provides access to method parameter names at runtime for classes processed by Flux's {@code WebParameterProcessor}.
 * <p>
 * This registry is primarily used for web handler method introspection, especially for resolving parameter names in
 * {@code @HandleWeb}-annotated methods and similar cases where Java reflection does not expose names reliably.
 * <p>
 * At compile time, the annotation processor generates a companion class per source class, which stores method
 * signatures and their parameter names. This registry loads those generated classes dynamically and offers lookup
 * methods for retrieving parameter names by method or individual parameter.
 * <p>
 * This utility is internal to Flux and not intended for general use.
 */
@RequiredArgsConstructor
public abstract class ParameterRegistry {

    private static final Function<Class<?>, ParameterRegistry> registryProvider = memoize(type -> {
        String packageName = type.getPackage().getName();
        String simpleClassName = type.getCanonicalName().replace(packageName + ".", "").replace(".", "_") + "_params";
        String fullClassName = packageName + "." + simpleClassName;
        return ReflectionUtils.asInstance(ReflectionUtils.classForName(fullClassName));
    });

    @Getter(AccessLevel.PRIVATE)
    private final Map<String, List<String>> methodParameters;
    private final Function<Executable, List<String>> parameterExtractor =
            memoize(m -> getMethodParameters().get(signature(m)));

    public List<String> getParameterNames(Executable method) {
        List<String> result = parameterExtractor.apply(method);
        if (result == null) {
            throw new IllegalStateException("Parameter names for method " + method + " not found");
        }
        return result;
    }

    public String getParameterName(Parameter parameter) {
        var method = parameter.getDeclaringExecutable();
        var parameters = Arrays.asList(method.getParameters());
        return getParameterNames(method).get(parameters.indexOf(parameter));
    }

    public static ParameterRegistry of(Class<?> type) {
        return registryProvider.apply(type);
    }

    public static String signature(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        String parameterTypes = method.getParameters()
                .stream()
                .map(param -> param.asType().toString())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return methodName + "(" + parameterTypes + ")";
    }

    public static String signature(Executable method) {
        return "%s(%s)".formatted(method.getName(),
                                  Arrays.stream(method.getParameterTypes()).map(Class::getCanonicalName)
                                          .collect(Collectors.joining(",")));
    }

}
