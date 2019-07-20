/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

import io.fluxcapacitor.common.ObjectUtils;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.ensureAccessible;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAllMethods;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

public class HandlerInspector {

    public static <M> List<Handler<M>> createHandlers(List<?> targets, Class<? extends Annotation> methodAnnotation,
                                                      List<ParameterResolver<? super M>> parameterResolvers) {
        return targets.stream().filter(o -> hasHandlerMethods(o.getClass(), methodAnnotation))
                .map(o -> createHandler(o, methodAnnotation, parameterResolvers)).collect(toList());
    }

    public static boolean hasHandlerMethods(Class<?> targetClass, Class<? extends Annotation> methodAnnotation) {
        return concat(getAllMethods(targetClass), stream(targetClass.getConstructors()))
                .anyMatch(m -> m.isAnnotationPresent(methodAnnotation));
    }

    public static <M> Handler<M> createHandler(Object target, Class<? extends Annotation> methodAnnotation,
                                               List<ParameterResolver<? super M>> parameterResolvers) {
        return new DefaultHandler<>(target, inspect(target.getClass(), methodAnnotation, parameterResolvers));
    }

    public static <M> HandlerInvoker<M> inspect(Class<?> type, Class<? extends Annotation> methodAnnotation,
                                                List<ParameterResolver<? super M>> parameterResolvers) {
        if (!hasHandlerMethods(type, methodAnnotation)) {
            throw new HandlerException(
                    format("Could not find methods with %s annotation on %s", methodAnnotation.getSimpleName(),
                           type.getSimpleName()));
        }
        return new ObjectHandlerInvoker<>(type, concat(getAllMethods(type), stream(type.getConstructors()))
                .filter(m -> m.isAnnotationPresent(methodAnnotation))
                .map(m -> new MethodHandlerInvoker<>(m, type, parameterResolvers))
                .sorted(Comparator.naturalOrder())
                .collect(toList()));
    }

    protected static class MethodHandlerInvoker<M> implements HandlerInvoker<M>, Comparable<MethodHandlerInvoker<M>> {

        private final int methodDepth;
        private final Executable executable;
        private final boolean hasReturnValue;
        private final List<Function<? super M, Object>> parameterSuppliers;
        private final Predicate<? super M> matcher;

        protected MethodHandlerInvoker(Executable executable, Class enclosingType,
                                       List<ParameterResolver<? super M>> parameterResolvers) {
            this.methodDepth = executable instanceof Method ? methodDepth(executable, enclosingType) : 0;
            this.executable = ensureAccessible(executable);
            this.hasReturnValue =
                    !(executable instanceof Method) || !(((Method) executable).getReturnType()).equals(void.class);
            this.parameterSuppliers = getParameterSuppliers(executable, parameterResolvers);
            this.matcher = getMatcher(executable, parameterResolvers);
        }

        @Override
        public boolean canHandle(Object target, M message) {
            if (!matcher.test(message)) {
                return false;
            }
            if (target == null) {
                return Modifier.isStatic(executable.getModifiers()) || executable instanceof Constructor;
            }
            return !Modifier.isStatic(executable.getModifiers()) && executable instanceof Method;
        }

        @Override
        public Executable getMethod(Object target, M message) {
            return canHandle(target, message) ? executable : null;
        }

        @Override
        public boolean expectResult(Object target, M message) {
            return canHandle(target, message) && hasReturnValue;
        }

        @Override
        @SneakyThrows
        public Object invoke(Object target, M message) {
            try {
                if (executable instanceof Method) {
                    if (target == null && !Modifier.isStatic(executable.getModifiers())) {
                        throw new HandlerNotFoundException(
                                format("Found instance method on target class %s that can handle the message "
                                               + "but the target instance is null. Should the method be static?",
                                       executable.getDeclaringClass().getSimpleName()));
                    }
                    return ((Method) executable)
                            .invoke(target, parameterSuppliers.stream().map(s -> s.apply(message)).toArray());
                } else {
                    return ((Constructor) executable)
                            .newInstance(parameterSuppliers.stream().map(s -> s.apply(message)).toArray());
                }
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        protected List<Function<? super M, Object>> getParameterSuppliers(Executable method,
                                                                          List<ParameterResolver<? super M>> resolvers) {
            if (method.getParameterCount() == 0) {
                throw new HandlerException(format("Annotated method %s should contain at least one parameter", method));
            }
            return stream(method.getParameters())
                    .map(p -> resolvers.stream().map(r -> r.resolve(p)).filter(Objects::nonNull).findFirst()
                            .orElseThrow(() -> new HandlerException(format("Could not resolve parameter %s", p))))
                    .collect(toList());
        }

        protected Class<?> getPayloadType() {
            return executable.getParameterTypes()[0];
        }

        protected Predicate<M> getMatcher(Executable executable,
                                          List<ParameterResolver<? super M>> parameterResolvers) {
            return m -> {
                Parameter parameter = executable.getParameters()[0];
                for (ParameterResolver<? super M> resolver : parameterResolvers) {
                    if (resolver.matches(parameter, m)) {
                        return true;
                    }
                }
                return false;
            };
        }

        @Override
        public int compareTo(MethodHandlerInvoker<M> o) {
            int result = comparePayloads(getPayloadType(), o.getPayloadType());
            if (result == 0) {
                result = methodDepth - o.methodDepth;
            }
            if (result == 0) {
                result = executable.toGenericString().compareTo(o.executable.toGenericString());
            }
            return result;
        }

        private static int comparePayloads(Class<?> p1, Class<?> p2) {
            return Objects.equals(p1, p2) ? 0 : p1.isAssignableFrom(p2) ? 1 : p2.isAssignableFrom(p1) ? -1 : 0;
        }

        private static int methodDepth(Executable instanceMethod, Class instanceType) {
            return (int) ObjectUtils.iterate(instanceType, Class::getSuperclass, type
                    -> stream(type.getDeclaredMethods()).anyMatch(m -> m.equals(instanceMethod))).count();
        }
    }

    @AllArgsConstructor
    protected static class ObjectHandlerInvoker<M> implements HandlerInvoker<M> {
        private final Class<?> type;
        private final List<HandlerInvoker<M>> methodHandlers;

        @Override
        public boolean canHandle(Object target, M message) {
            return methodHandlers.stream().anyMatch(h -> h.canHandle(target, message));
        }

        @Override
        public Executable getMethod(Object target, M message) {
            return methodHandlers.stream().map(h -> h.getMethod(target, message)).filter(Objects::nonNull).findAny()
                    .orElse(null);
        }

        @Override
        public boolean expectResult(Object target, M message) {
            return methodHandlers.stream().anyMatch(h -> h.expectResult(target, message));
        }

        @Override
        public Object invoke(Object target, M message) {
            Optional<HandlerInvoker<M>> delegate =
                    methodHandlers.stream().filter(d -> d.canHandle(target, message)).findFirst();
            if (!delegate.isPresent()) {
                throw new HandlerNotFoundException(format("No method found on %s that could handle %s", type, message));
            }
            return delegate.get().invoke(target, message);
        }
    }

    @AllArgsConstructor
    protected static class DefaultHandler<M> implements Handler<M> {
        private final Object target;
        private final HandlerInvoker<M> invoker;

        @Override
        public boolean canHandle(M message) {
            return invoker.canHandle(target, message);
        }

        @Override
        public Executable getMethod(M message) {
            return invoker.getMethod(target, message);
        }

        @Override
        public Object invoke(M message) {
            return invoker.invoke(target, message);
        }

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public String toString() {
            return "DefaultHandler{target=" + target + '}';
        }
    }
}
