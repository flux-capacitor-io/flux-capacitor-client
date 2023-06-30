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

import io.fluxcapacitor.common.reflection.DefaultMemberInvoker;
import io.fluxcapacitor.common.reflection.MemberInvoker;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.handling.HandlerInspector.MethodHandlerMatcher.comparator;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.ensureAccessible;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAllMethods;
import static java.util.Arrays.stream;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

public class HandlerInspector {

    public static boolean hasHandlerMethods(Class<?> targetClass,
                                            HandlerConfiguration<?> handlerConfiguration) {
        return concat(getAllMethods(targetClass).stream(), stream(targetClass.getConstructors()))
                .anyMatch(m -> handlerConfiguration.methodMatches(targetClass, m));
    }

    public static <M> Handler<M> createHandler(Object target, Class<? extends Annotation> methodAnnotation) {
        return createHandler(target, methodAnnotation, List.of((p, a) -> o -> o));
    }

    public static <M> Handler<M> createHandler(Object target, Class<? extends Annotation> methodAnnotation,
                                               List<ParameterResolver<? super M>> parameterResolvers) {
        return createHandler(target, parameterResolvers,
                             HandlerConfiguration.builder().methodAnnotation(methodAnnotation).build());
    }

    public static <M> Handler<M> createHandler(Supplier<?> targetSupplier, Class<?> targetClass,
                                               List<ParameterResolver<? super M>> parameterResolvers,
                                               HandlerConfiguration<? super M> config) {
        return new DefaultHandler<>(targetSupplier, inspect(targetClass, parameterResolvers, config));
    }

    public static <M> Handler<M> createHandler(Object target, List<ParameterResolver<? super M>> parameterResolvers,
                                               HandlerConfiguration<? super M> config) {
        return new DefaultHandler<>(() -> target, inspect(target.getClass(), parameterResolvers, config));
    }

    public static <M> HandlerMatcher<Object, M> inspect(Class<?> c,
                                                        List<ParameterResolver<? super M>> parameterResolvers,
                                                        Class<? extends Annotation> methodAnnotation) {
        return inspect(c, parameterResolvers,
                       HandlerConfiguration.builder().methodAnnotation(methodAnnotation).build());
    }

    public static <M> HandlerMatcher<Object, M> inspect(Class<?> c,
                                                        List<ParameterResolver<? super M>> parameterResolvers,
                                                        HandlerConfiguration<? super M> config) {
        return new ObjectHandlerMatcher<>(concat(getAllMethods(c).stream(), stream(c.getDeclaredConstructors()))
                                                  .filter(m -> config.methodMatches(c, m))
                                                  .flatMap(m -> Stream.of(
                                                          new MethodHandlerMatcher<>(m, c, parameterResolvers, config)))
                                                  .sorted(comparator).collect(toList()),
                                          config.invokeMultipleMethods());
    }

    @Getter
    public static class MethodHandlerMatcher<M> implements HandlerMatcher<Object, M> {
        protected static final Comparator<MethodHandlerMatcher<?>> comparator = Comparator.comparing(
                        (Function<MethodHandlerMatcher<?>, Integer>) MethodHandlerMatcher::getPriority, reverseOrder())
                .thenComparing(
                        (Function<MethodHandlerMatcher<?>, Class<?>>) MethodHandlerMatcher::getClassForSpecificity,
                        (o1, o2)
                                -> Objects.equals(o1, o2) ? 0
                                : o1 == null ? 1 : o2 == null ? -1
                                : o1.isAssignableFrom(o2) ? 1
                                : o2.isAssignableFrom(o1) ? -1
                                : o1.isInterface() && !o2.isInterface() ? 1
                                : !o1.isInterface() && o2.isInterface() ? -1
                                : specificity(o2) - specificity(o1))
                .thenComparingInt(a -> -a.getParameterCount())
                .thenComparingInt(MethodHandlerMatcher::getMethodIndex);

        private final int methodIndex;
        private final Executable executable;
        private final Parameter[] parameters;
        private final int parameterCount;
        private final boolean staticMethod;
        private final MemberInvoker invoker;
        private final boolean hasReturnValue;
        private final Class<?> classForSpecificity;
        private final Annotation methodAnnotation;
        private final int priority;
        private final boolean passive;
        private final List<ParameterResolver<? super M>> parameterResolvers;
        private final HandlerConfiguration<? super M> config;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private final Optional<Object> emptyResult = Optional.of(void.class);

        public MethodHandlerMatcher(Executable executable, Class<?> enclosingType,
                                    List<ParameterResolver<? super M>> parameterResolvers,
                                    @NonNull HandlerConfiguration<? super M> config) {
            this.parameterResolvers = parameterResolvers;
            this.config = config;
            this.methodIndex = executable instanceof Method ? methodIndex((Method) executable, enclosingType) : 0;
            this.executable = ensureAccessible(executable);
            this.parameters = this.executable.getParameters();
            this.parameterCount = this.parameters.length;
            this.staticMethod = Modifier.isStatic(this.executable.getModifiers());
            this.hasReturnValue =
                    !(executable instanceof Method) || !(((Method) executable).getReturnType()).equals(void.class);
            this.methodAnnotation = config.getAnnotation(executable).orElse(null);
            this.classForSpecificity = computeClassForSpecificity();
            this.priority = getPriority(methodAnnotation);
            this.passive = isPassive(methodAnnotation);
            this.invoker = DefaultMemberInvoker.asInvoker(this.executable);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Optional<HandlerInvoker> findInvoker(Object target, M m) {
            if (!config.messageFilter().test(m, executable)
                || (target == null
                    ? !(executable instanceof Constructor) && !staticMethod
                    : !(executable instanceof Method) || staticMethod)) {
                return Optional.empty();
            }

            if (parameterCount == 0) {
                return Optional.of(new MethodHandlerInvoker(target) {
                    @Override
                    public Object invoke(BiFunction<Object, Object, Object> combiner) {
                        return invoker.invoke(target);
                    }
                });
            }

            Function<? super M, Object>[] matchingResolvers = new Function[parameterCount];
            for (int i = 0; i < parameterCount; i++) {
                Parameter p = parameters[i];
                ParameterResolver<? super M> matchingResolver = null;
                for (ParameterResolver<? super M> r : parameterResolvers) {
                    if (r.matches(p, methodAnnotation, m, target)) {
                        matchingResolver = r;
                        break;
                    }
                }
                if (matchingResolver == null || !matchingResolver.filterMessage(m, p)) {
                    return Optional.empty();
                }
                matchingResolvers[i] = matchingResolver.resolve(p, methodAnnotation);
            }
            return Optional.of(new MethodHandlerInvoker(target) {
                @Override
                public Object invoke(BiFunction<Object, Object, Object> combiner) {
                    return invoker.invoke(target, parameterCount, i -> matchingResolvers[i].apply(m));
                }
            });
        }

        protected Class<?> computeClassForSpecificity() {
            for (Parameter p : parameters) {
                for (ParameterResolver<? super M> r : parameterResolvers) {
                    if (r.determinesSpecificity()) {
                        Function<? super M, Object> resolver = r.resolve(p, methodAnnotation);
                        if (resolver != null) {
                            return p.getType();
                        }
                    }
                }
            }
            return null;
        }

        protected static int specificity(Class<?> type) {
            int depth = 0;
            Class<?> t = type;
            if (type.isInterface()) {
                while (t.getInterfaces().length > 0) {
                    depth++;
                    t = t.getInterfaces()[0];
                }
            } else {
                while (t != null) {
                    depth++;
                    t = t.getSuperclass();
                }
            }
            return depth;
        }

        protected int methodIndex(Method instanceMethod, Class<?> instanceType) {
            return ReflectionUtils.getAllMethods(instanceType).indexOf(instanceMethod);
        }

        @SneakyThrows
        protected int getPriority(Annotation annotation) {
            if (annotation == null) {
                return 0;
            }
            Optional<Method> match = Arrays.stream(annotation.annotationType().getMethods())
                    .filter(m -> m.getName().equals("priority")).findFirst();
            if (match.isPresent()) {
                return (int) match.get().invoke(annotation);
            }
            return 0;
        }

        @SneakyThrows
        protected boolean isPassive(Annotation annotation) {
            if (annotation == null) {
                return false;
            }
            Optional<Method> match = Arrays.stream(annotation.annotationType().getMethods())
                    .filter(m -> m.getName().equals("passive")).findFirst();
            if (match.isPresent()) {
                return (boolean) match.get().invoke(annotation);
            }
            return false;
        }

        @AllArgsConstructor
        protected abstract class MethodHandlerInvoker implements HandlerInvoker {
            private final Object target;

            @Override
            public Object getTarget() {
                return target;
            }

            @Override
            public Executable getMethod() {
                return executable;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <A extends Annotation> A getMethodAnnotation() {
                return (A) methodAnnotation;
            }

            @Override
            public boolean expectResult() {
                return hasReturnValue;
            }

            @Override
            public boolean isPassive() {
                return passive;
            }

            @Override
            public String toString() {
                return Optional.ofNullable(target).map(o -> {
                    String simpleName = o.getClass().getSimpleName();
                    return String.format("\"%s\"", simpleName.isEmpty() ? o.getClass() : simpleName);
                }).orElse("MethodHandlerInvoker");
            }
        }
    }

    @AllArgsConstructor
    public static class ObjectHandlerMatcher<M> implements HandlerMatcher<Object, M> {
        private final List<HandlerMatcher<Object, M>> methodHandlers;
        private final boolean invokeMultipleMethods;
        @Override
        public Optional<HandlerInvoker> findInvoker(Object target, M message) {
            if (invokeMultipleMethods) {
                HandlerInvoker invoker = null;
                for (HandlerMatcher<Object, M> d : methodHandlers) {
                    Optional<HandlerInvoker> s = d.findInvoker(target, message);
                    if (s.isPresent()) {
                        invoker = invoker == null ? s.get() : invoker.combine(s.get());
                    }
                }
                return Optional.ofNullable(invoker);
            }

            for (HandlerMatcher<Object, M> d : methodHandlers) {
                Optional<HandlerInvoker> s = d.findInvoker(target, message);
                if (s.isPresent()) {
                    return s;
                }
            }
            return Optional.empty();
        }

    }

    @AllArgsConstructor
    @Slf4j
    public static class DefaultHandler<M> implements Handler<M> {
        private final Supplier<?> targetSupplier;
        private final HandlerMatcher<Object, M> invoker;

        @Override
        public Object getTarget() {
            return targetSupplier.get();
        }

        @Override
        public Optional<HandlerInvoker> findInvoker(M message) {
            return invoker.findInvoker(getTarget(), message);
        }

        @Override
        public String toString() {
            return Optional.ofNullable(getTarget()).map(o -> {
                        String simpleName = o.getClass().getSimpleName();
                        return String.format("\"%s\"", simpleName.isEmpty() ? o.getClass() : simpleName);
                    }).orElse("DefaultHandler");
        }
    }
}
