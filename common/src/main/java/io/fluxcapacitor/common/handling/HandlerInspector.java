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

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.handling.HandlerConfiguration.defaultHandlerConfiguration;
import static io.fluxcapacitor.common.handling.HandlerInspector.MethodHandlerInvoker.comparator;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.ensureAccessible;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAllMethods;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;

public class HandlerInspector {

    public static boolean hasHandlerMethods(Class<?> targetClass, Class<? extends Annotation> methodAnnotation,
                                            HandlerConfiguration<?> handlerConfiguration) {
        return concat(getAllMethods(targetClass).stream(), stream(targetClass.getConstructors()))
                .anyMatch(m -> m.isAnnotationPresent(methodAnnotation) && handlerConfiguration.handlerFilter()
                        .test(targetClass, m));
    }

    public static <M> Handler<M> createHandler(Object target, Class<? extends Annotation> methodAnnotation,
                                               List<ParameterResolver<? super M>> parameterResolvers) {
        return createHandler(target, methodAnnotation, parameterResolvers, defaultHandlerConfiguration());
    }

    public static <M> Handler<M> createHandler(Object target, Class<? extends Annotation> methodAnnotation,
                                               List<ParameterResolver<? super M>> parameterResolvers,
                                               HandlerConfiguration<M> handlerConfiguration) {
        return new DefaultHandler<>(target, inspect(target.getClass(), methodAnnotation, parameterResolvers,
                                                    handlerConfiguration));
    }

    public static <M> HandlerInvoker<M> inspect(Class<?> type, Class<? extends Annotation> methodAnnotation,
                                                List<ParameterResolver<? super M>> parameterResolvers,
                                                HandlerConfiguration<M> handlerConfiguration) {
        return new ObjectHandlerInvoker<>(type, concat(getAllMethods(type).stream(), stream(type.getDeclaredConstructors()))
                .filter(m -> m.isAnnotationPresent(methodAnnotation) && handlerConfiguration.handlerFilter().test(type, m))
                .map(m -> handlerConfiguration.invokerFactory().create(m, type, parameterResolvers, methodAnnotation))
                .sorted(comparator).collect(toList()), handlerConfiguration.invokeMultipleMethods());
    }

    @Getter
    public static class MethodHandlerInvoker<M> implements HandlerInvoker<M> {
        protected static final Comparator<MethodHandlerInvoker<?>> comparator =
                comparing((Function<MethodHandlerInvoker<?>, Class<?>>) MethodHandlerInvoker::getPayloadType, (o1, o2)
                        -> Objects.equals(o1, o2) ? 0
                        : o1.isAssignableFrom(o2) || (o1.isInterface() && !o2.isInterface()) ? 1
                        : o2.isAssignableFrom(o1) || (!o1.isInterface() && o2.isInterface()) ? -1
                        : specificity(o2) - specificity(o1))
                        .thenComparing(MethodHandlerInvoker::getMethodIndex);

        private final int methodIndex;
        private final Executable executable;
        private final boolean hasReturnValue;
        private final List<Function<? super M, Object>> parameterSuppliers;
        private final Predicate<? super M> matcher;
        private final Class<? extends Annotation> methodAnnotation;

        public MethodHandlerInvoker(Executable executable, Class<?> enclosingType,
                                    List<ParameterResolver<? super M>> parameterResolvers,
                                    Class<? extends Annotation> methodAnnotation) {
            this.methodAnnotation = methodAnnotation;
            this.methodIndex = executable instanceof Method ? methodIndex((Method) executable, enclosingType) : 0;
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
                    return ((Constructor<?>) executable)
                            .newInstance(parameterSuppliers.stream().map(s -> s.apply(message)).toArray());
                }
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @Override
        @SneakyThrows
        public boolean isPassive(Object target, M message) {
            if (!canHandle(target, message)) {
                return true;
            }
            Annotation annotation = executable.getAnnotation(methodAnnotation);
            Optional<Method> isPassive = Arrays.stream(methodAnnotation.getMethods())
                    .filter(m -> m.getName().equals("passive")).findFirst();
            if (isPassive.isPresent()) {
                return (boolean) isPassive.get().invoke(annotation);
            }
            return false;
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

        protected static int methodIndex(Method instanceMethod, Class<?> instanceType) {
            return ReflectionUtils.getAllMethods(instanceType).indexOf(instanceMethod);
        }
    }

    @AllArgsConstructor
    public static class ObjectHandlerInvoker<M> implements HandlerInvoker<M> {
        private final Class<?> type;
        private final List<HandlerInvoker<M>> methodHandlers;
        private final boolean invokeMultipleMethods;

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
            Stream<HandlerInvoker<M>> handlerStream = methodHandlers.stream().filter(d -> d.canHandle(target, message));
            if (invokeMultipleMethods) {
                return handlerStream.map(h -> h.invoke(target, message)).filter(Objects::nonNull)
                        .reduce((a, b) -> b).orElse(null);
            }
            Optional<HandlerInvoker<M>> delegate = handlerStream.findFirst();
            if (!delegate.isPresent()) {
                throw new HandlerNotFoundException(format("No method found on %s that could handle %s", type, message));
            }
            return delegate.get().invoke(target, message);
        }

        @Override
        public boolean isPassive(Object target, M message) {
            return methodHandlers.stream().allMatch(h -> h.isPassive(target, message));
        }

    }

    @AllArgsConstructor
    public static class DefaultHandler<M> implements Handler<M> {
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
        public boolean isPassive(M message) {
            return invoker.isPassive(target, message);
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
            return Optional.ofNullable(target).map(o -> String.format("\"%s\"", o.getClass().getSimpleName()))
                    .orElse("DefaultHandler");
        }
    }
}
