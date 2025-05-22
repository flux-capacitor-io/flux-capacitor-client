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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.handling.HandlerInspector.MethodHandlerMatcher.comparator;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.ensureAccessible;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAllMethods;
import static java.util.Arrays.stream;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

/**
 * Utility for inspecting and constructing handler components from annotated methods.
 * <p>
 * The {@code HandlerInspector} scans classes for methods that match handler criteria (e.g., {@code @HandleCommand}),
 * and constructs {@link Handler}, {@link HandlerMatcher}, and {@link HandlerInvoker} instances based on those methods.
 * It supports both stateless (singleton) and stateful handlers by accepting target suppliers and parameter resolvers.
 * </p>
 *
 * <p>
 * This class is the core of the handler discovery and resolution mechanism in Flux Capacitor, bridging the gap between
 * annotated user-defined classes and runtime message dispatch infrastructure.
 * </p>
 *
 * <h2>Capabilities</h2>
 * <ul>
 *     <li>Detect handler methods on a class based on annotations and signatures</li>
 *     <li>Create {@link Handler} instances that provide lifecycle-aware invocation logic</li>
 *     <li>Build {@link HandlerMatcher}s that analyze message applicability</li>
 *     <li>Support parameter resolution for dependency injection and message binding</li>
 * </ul>
 *
 * @see Handler
 * @see HandlerMatcher
 * @see HandlerInvoker
 */
public class HandlerInspector {

    /**
     * Returns whether the given class contains at least one method or constructor that matches the handler
     * configuration.
     *
     * @param targetClass          the class to inspect
     * @param handlerConfiguration the handler configuration to match against
     * @return {@code true} if at least one method or constructor is a valid handler
     */
    public static boolean hasHandlerMethods(Class<?> targetClass,
                                            HandlerConfiguration<?> handlerConfiguration) {
        return concat(getAllMethods(targetClass).stream(), stream(targetClass.getConstructors()))
                .anyMatch(m -> handlerConfiguration.methodMatches(targetClass, m));
    }

    /**
     * Creates a {@link Handler} for the given target object and annotation type. This variant uses a default no-op
     * parameter resolver.
     *
     * @param target           the handler instance
     * @param methodAnnotation the annotation to detect handler methods (e.g. {@code @HandleCommand})
     * @param <M>              the message type
     * @return a new {@code Handler} instance
     */
    public static <M> Handler<M> createHandler(Object target, Class<? extends Annotation> methodAnnotation) {
        return createHandler(target, methodAnnotation, List.of((p, a) -> o -> o));
    }

    /**
     * Creates a {@link Handler} backed by a target supplier and parameter resolvers. Suitable for stateful handlers
     * where the instance depends on the message content.
     *
     * @param target             the handler instance
     * @param parameterResolvers resolvers for handler method parameters
     * @param methodAnnotation   the annotation to detect handler methods (e.g. {@code @HandleCommand})
     * @param <M>                the message type
     * @return a handler capable of resolving and invoking methods on the target
     */
    public static <M> Handler<M> createHandler(Object target, Class<? extends Annotation> methodAnnotation,
                                               List<ParameterResolver<? super M>> parameterResolvers) {
        return createHandler(target, parameterResolvers,
                             HandlerConfiguration.builder().methodAnnotation(methodAnnotation).build());
    }

    /**
     * Creates a {@link Handler} backed by a target supplier and parameter resolvers. Suitable for stateful handlers
     * where the instance depends on the message content.
     *
     * @param target             the handler instance
     * @param parameterResolvers resolvers for handler method parameters
     * @param config             handler configuration settings
     * @param <M>                the message type
     * @return a handler capable of resolving and invoking methods on the target
     */
    public static <M> Handler<M> createHandler(Object target, List<ParameterResolver<? super M>> parameterResolvers,
                                               HandlerConfiguration<? super M> config) {
        return createHandler(m -> target, target.getClass(), parameterResolvers, config);
    }

    /**
     * Creates a {@link Handler} backed by a target supplier and parameter resolvers. Suitable for stateful handlers
     * where the instance depends on the message content.
     *
     * @param targetSupplier     provides the handler instance to use for a given message
     * @param targetClass        the class containing handler methods
     * @param parameterResolvers resolvers for handler method parameters
     * @param config             handler configuration settings
     * @param <M>                the message type
     * @return a handler capable of resolving and invoking methods on the target
     */
    public static <M> Handler<M> createHandler(Function<M, ?> targetSupplier, Class<?> targetClass,
                                               List<ParameterResolver<? super M>> parameterResolvers,
                                               HandlerConfiguration<? super M> config) {
        return new DefaultHandler<>(targetClass, targetSupplier, inspect(targetClass, parameterResolvers, config));
    }

    /**
     * Inspects the given class for methods matching the specified annotation and builds a {@link HandlerMatcher}.
     * <p>
     * This matcher can later be used to resolve a {@link HandlerInvoker} for a target object and message.
     * </p>
     *
     * @param targetClass        the class containing handler methods
     * @param parameterResolvers resolvers for handler method parameters
     * @param methodAnnotation   the annotation to detect handler methods (e.g. {@code @HandleCommand})
     */
    public static <M> HandlerMatcher<Object, M> inspect(Class<?> targetClass,
                                                        List<ParameterResolver<? super M>> parameterResolvers,
                                                        Class<? extends Annotation> methodAnnotation) {
        return inspect(targetClass, parameterResolvers,
                       HandlerConfiguration.builder().methodAnnotation(methodAnnotation).build());
    }

    /**
     * Inspects the given class for methods matching the specified annotation and builds a {@link HandlerMatcher}.
     * <p>
     * This matcher can later be used to resolve a {@link HandlerInvoker} for a target object and message.
     * </p>
     *
     * @param targetClass        the class containing handler methods
     * @param parameterResolvers resolvers for handler method parameters
     * @param config             handler configuration settings
     */
    public static <M> HandlerMatcher<Object, M> inspect(Class<?> targetClass,
                                                        List<ParameterResolver<? super M>> parameterResolvers,
                                                        HandlerConfiguration<? super M> config) {
        return new ObjectHandlerMatcher<>(
                concat(getAllMethods(targetClass).stream(), stream(targetClass.getDeclaredConstructors()))
                        .filter(m -> config.methodMatches(targetClass, m))
                        .flatMap(m -> Stream.of(
                                new MethodHandlerMatcher<>(m, targetClass, parameterResolvers, config)))
                        .sorted(comparator).collect(toList()),
                config.invokeMultipleMethods());
    }

    /**
     * A matcher that encapsulates metadata and resolution logic for a single handler method or constructor.
     * <p>
     * Resolves parameter values using {@link ParameterResolver}s and builds a {@link HandlerInvoker}
     * for invoking the method on a given target instance.
     * </p>
     *
     * <p>
     * This matcher supports prioritization, specificity analysis, and filtering based on annotations and parameters.
     * </p>
     */
    @Getter
    public static class MethodHandlerMatcher<M> implements HandlerMatcher<Object, M> {
        protected static final Comparator<MethodHandlerMatcher<?>> comparator = Comparator.comparing(
                        (Function<MethodHandlerMatcher<?>, Integer>) MethodHandlerMatcher::getPriority, reverseOrder())
                .thenComparing(
                        (Function<MethodHandlerMatcher<?>, Class<?>>) MethodHandlerMatcher::getClassForSpecificity,
                        ReflectionUtils.getClassSpecificityComparator())
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
        private final Class<? extends Annotation> methodAnnotationType;
        private final int priority;
        private final boolean passive;
        private final Class<?> targetClass;
        private final List<ParameterResolver<? super M>> parameterResolvers;
        private final HandlerConfiguration<? super M> config;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private final Optional<Object> emptyResult = Optional.of(void.class);

        public MethodHandlerMatcher(Executable executable, Class<?> targetClass,
                                    List<ParameterResolver<? super M>> parameterResolvers,
                                    @NonNull HandlerConfiguration<? super M> config) {
            this.targetClass = targetClass;
            this.parameterResolvers = parameterResolvers;
            this.config = config;
            this.methodIndex = executable instanceof Method ? methodIndex((Method) executable, targetClass) : 0;
            this.executable = ensureAccessible(executable);
            this.parameters = this.executable.getParameters();
            this.parameterCount = this.parameters.length;
            this.staticMethod = Modifier.isStatic(this.executable.getModifiers());
            this.hasReturnValue =
                    !(executable instanceof Method) || !(((Method) executable).getReturnType()).equals(void.class);
            this.methodAnnotation = config.getAnnotation(executable).orElse(null);
            this.methodAnnotationType = Optional.ofNullable(this.methodAnnotation).map(Annotation::annotationType)
                    .orElse(null);
            this.classForSpecificity = computeClassForSpecificity();
            this.priority = getPriority(methodAnnotation);
            this.passive = isPassive(methodAnnotation);
            this.invoker = DefaultMemberInvoker.asInvoker(this.executable);
        }

        @Override
        public boolean canHandle(M message) {
            return prepareInvoker(message).isPresent();
        }

        @Override
        public Stream<Executable> matchingMethods(M message) {
            return canHandle(message) ? Stream.of(executable) : Stream.empty();
        }

        @SuppressWarnings("unchecked")
        protected Optional<Function<Object, HandlerInvoker>> prepareInvoker(M m) {
            if (!config.messageFilter().test(m, executable, methodAnnotationType)) {
                return Optional.empty();
            }

            if (parameterCount == 0) {
                return Optional.of(target -> new MethodHandlerInvoker() {
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
                    if (r.matches(p, methodAnnotation, m)) {
                        matchingResolver = r;
                        break;
                    }
                }
                if (matchingResolver == null || !matchingResolver.filterMessage(m, p)) {
                    return Optional.empty();
                }
                matchingResolvers[i] = matchingResolver.resolve(p, methodAnnotation);
            }
            return Optional.of(target -> new MethodHandlerInvoker() {
                @Override
                public Object invoke(BiFunction<Object, Object, Object> combiner) {
                    return invoker.invoke(target, parameterCount, i -> matchingResolvers[i].apply(m));
                }
            });
        }

        @Override
        public Optional<HandlerInvoker> getInvoker(Object target, M m) {
            if (target == null
                    ? !(executable instanceof Constructor) && !staticMethod
                    : !(executable instanceof Method) || staticMethod) {
                return Optional.empty();
            }
            return prepareInvoker(m).map(f -> f.apply(target));
        }

        protected Class<?> computeClassForSpecificity() {
            Class<?> handlerType = config.messageFilter().getLeastSpecificAllowedClass(
                    executable, methodAnnotationType).orElse(null);
            for (Parameter p : parameters) {
                for (ParameterResolver<? super M> r : parameterResolvers) {
                    if (r.determinesSpecificity()) {
                        Function<? super M, Object> resolver = r.resolve(p, methodAnnotation);
                        if (resolver != null) {
                            Class<?> parameterType = p.getType();
                            if (handlerType != null && !handlerType.isAssignableFrom(parameterType)) {
                                return handlerType;
                            }
                            return parameterType;
                        }
                    }
                }
            }
            return handlerType;
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

            @Override
            public Class<?> getTargetClass() {
                return targetClass;
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
                return Optional.ofNullable(targetClass).map(c -> {
                    String simpleName = c.getSimpleName();
                    return String.format("\"%s\"", simpleName.isEmpty() ? c : simpleName);
                }).orElse("MethodHandlerInvoker");
            }
        }
    }

    /**
     * A composite {@link HandlerMatcher} that delegates to a list of individual matchers.
     * <p>
     * Supports invoking one or multiple methods depending on configuration.
     * </p>
     */
    @AllArgsConstructor
    public static class ObjectHandlerMatcher<M> implements HandlerMatcher<Object, M> {
        private final List<HandlerMatcher<Object, M>> methodHandlers;
        private final boolean invokeMultipleMethods;

        @Override
        public boolean canHandle(M message) {
            for (HandlerMatcher<Object, M> d : methodHandlers) {
                if (d.canHandle(message)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Stream<Executable> matchingMethods(M message) {
            return methodHandlers.stream().flatMap(m -> m.matchingMethods(message));
        }

        @Override
        public Optional<HandlerInvoker> getInvoker(Object target, M message) {
            if (invokeMultipleMethods) {
                List<HandlerInvoker> invokers = new ArrayList<>();
                for (HandlerMatcher<Object, M> d : methodHandlers) {
                    var s = d.getInvoker(target, message);
                    s.ifPresent(invokers::add);
                }
                return HandlerInvoker.join(invokers);
            }
            for (HandlerMatcher<Object, M> d : methodHandlers) {
                var s = d.getInvoker(target, message);
                if (s.isPresent()) {
                    return s;
                }
            }
            return Optional.empty();
        }

    }

}
