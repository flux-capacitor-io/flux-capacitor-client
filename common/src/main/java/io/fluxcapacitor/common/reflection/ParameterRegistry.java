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
 * Registry for accessing method parameter names at runtime in environments where Java reflection does not retain them
 * (e.g. Java applications compiled without debug information).
 * <p>
 * This class works in tandem with annotation processors, which generate a companion {@code _params} class per handler
 * class at compile time (see e.g.: {@code WebParameterProcessor}). The generated class extends this
 * {@code ParameterRegistry} and provides a static map from method signatures to parameter name lists.
 *
 * <p>At runtime, the framework uses this registry to retrieve parameter names for handler methods
 * annotated with web-related annotations such as {@code @QueryParam}, {@code @PathParam}, etc. This is especially
 * important for Java applications, where parameter names are not always available through standard reflection.
 *
 * <p>For example, given a method:
 * <pre>{@code
 * public void getUser(@PathParam("id") String userId) { ... }
 * }</pre>
 * the generated registry allows the framework to resolve "userId" even if it's not available via reflection.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Loads generated registries dynamically using class naming conventions</li>
 *   <li>Provides lookup by {@link Executable} or {@link Parameter}</li>
 *   <li>Memoizes results for performance</li>
 *   <li>Uses method signature format: {@code methodName(paramType1,paramType2)}</li>
 * </ul>
 *
 * <h3>Internal Use Only</h3>
 * This utility is not intended for application developers to use directly. It is used internally
 * by Flux Capacitor's web framework components.
 */
@RequiredArgsConstructor
public abstract class ParameterRegistry {

    /**
     * Memoized provider that dynamically loads the generated `_params` class for a given type.
     */
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

    /**
     * Retrieves the parameter names for a given method.
     *
     * @param method the reflective {@link Executable} (method or constructor)
     * @return the list of parameter names, in declaration order
     * @throws IllegalStateException if no parameter names are found
     */
    public List<String> getParameterNames(Executable method) {
        List<String> result = parameterExtractor.apply(method);
        if (result == null) {
            throw new IllegalStateException("Parameter names for method " + method + " not found");
        }
        return result;
    }

    /**
     * Retrieves the name of a specific method parameter.
     *
     * @param parameter the {@link Parameter} to look up
     * @return the parameter name, as recorded in the generated registry
     */
    public String getParameterName(Parameter parameter) {
        var method = parameter.getDeclaringExecutable();
        var parameters = Arrays.asList(method.getParameters());
        return getParameterNames(method).get(parameters.indexOf(parameter));
    }

    /**
     * Returns the registry instance for the specified class, loading and memoizing the generated {@code _params} class
     * on first access.
     *
     * @param type the source class for which to obtain a registry
     * @return the corresponding {@link ParameterRegistry} instance
     */
    public static ParameterRegistry of(Class<?> type) {
        return registryProvider.apply(type);
    }

    /**
     * Generates a string representation of a method signature using compile-time elements.
     *
     * @param method the method element
     * @return a signature string in the format {@code methodName(paramType1,paramType2)}
     */
    public static String signature(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        String parameterTypes = method.getParameters()
                .stream()
                .map(param -> param.asType().toString())
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return methodName + "(" + parameterTypes + ")";
    }

    /**
     * Generates a string representation of a method signature using a runtime reflection {@link Executable}.
     *
     * @param method the method or constructor
     * @return a signature string in the format {@code methodName(paramType1,paramType2)}
     */
    public static String signature(Executable method) {
        return "%s(%s)".formatted(method.getName(),
                                  Arrays.stream(method.getParameterTypes()).map(Class::getCanonicalName)
                                          .collect(Collectors.joining(",")));
    }
}
