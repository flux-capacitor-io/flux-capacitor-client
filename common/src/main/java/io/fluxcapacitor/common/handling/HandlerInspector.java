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

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

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
        return concat(stream(targetClass.getMethods()), stream(targetClass.getConstructors()))
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
                    String.format("Could not find methods with %s annotation on %s", methodAnnotation.getSimpleName(),
                                  type.getSimpleName()));
        }
        return new ObjectHandlerInvoker<>(type, concat(stream(type.getMethods()), stream(type.getConstructors()))
                .filter(m -> m.isAnnotationPresent(methodAnnotation))
                .map(m -> new MethodHandlerInvoker<>(m, parameterResolvers))
                .sorted(Comparator.naturalOrder())
                .collect(toList()));
    }

    protected static class MethodHandlerInvoker<M> implements HandlerInvoker<M>, Comparable<MethodHandlerInvoker<M>> {

        private final Executable executable;
        private final List<Function<? super M, Object>> parameterSuppliers;
        private final Function<? super M, ? extends Class<?>> payloadTypeSupplier;

        protected MethodHandlerInvoker(Executable executable,
                                       List<ParameterResolver<? super M>> parameterResolvers) {
            this.executable = executable;
            this.parameterSuppliers = getParameterSuppliers(executable, parameterResolvers);
            this.payloadTypeSupplier = getPayloadTypeSupplier(executable, parameterResolvers);
            if (!executable.isAccessible()) {
                executable.setAccessible(true);
            }
        }

        @Override
        public boolean canHandle(M message) {
            return getPayloadType().isAssignableFrom(payloadTypeSupplier.apply(message));
        }

        @Override
        public Object invoke(Object target, M message) {
            try {
                if (executable instanceof Method) {
                    return ((Method) executable)
                            .invoke(target, parameterSuppliers.stream().map(s -> s.apply(message)).toArray());
                } else {
                    return ((Constructor) executable)
                            .newInstance(parameterSuppliers.stream().map(s -> s.apply(message)).toArray());
                }
            } catch (InstantiationException e) {
                throw new HandlerException(format("Failed to create an instance using constructor %s", executable), e);
            } catch (InvocationTargetException e) {
                Exception thrown = e;
                if (e.getCause() instanceof Exception) {
                    thrown = (Exception) e.getCause();
                }
                throw new HandlerException(format("Target failed to handle a %s, method: %s", message, executable),
                                           thrown);
            } catch (IllegalAccessException e) {
                throw new HandlerException(format("Failed to handle a %s, method: %s", message, executable), e);
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

        protected Function<? super M, ? extends Class<?>> getPayloadTypeSupplier(Executable method,
                                                                                 List<ParameterResolver<? super M>> resolvers) {
            Parameter parameter = method.getParameters()[0];
            return resolvers.stream().map(r -> r.resolveClass(parameter)).findFirst().orElseThrow(
                    () -> new HandlerException("Could not determine payload type for method " + method));
        }

        protected Class<?> getPayloadType() {
            return executable.getParameterTypes()[0];
        }

        @Override
        @SuppressWarnings("NullableProblems")
        public int compareTo(MethodHandlerInvoker<M> o) {
            int result = comparePayloads(getPayloadType(), o.getPayloadType());
            if (result == 0) {
                result = executable.toGenericString().compareTo(o.executable.toGenericString());
            }
            return result;
        }

        private static int comparePayloads(Class<?> p1, Class<?> p2) {
            return Objects.equals(p1, p2) ? 0 : p1.isAssignableFrom(p2) ? 1 :
                    p2.isAssignableFrom(p1) ? -1 : Long.compare(depthOf(p2), depthOf(p1));
        }

        private static long depthOf(Class payload) {
            return ObjectUtils.iterate(payload, Class::getSuperclass, Objects::isNull).count();
        }
    }

    @AllArgsConstructor
    protected static class ObjectHandlerInvoker<M> implements HandlerInvoker<M> {
        private final Class<?> type;
        private final List<HandlerInvoker<M>> methodHandlers;

        @Override
        public boolean canHandle(M message) {
            return methodHandlers.stream().anyMatch(h -> h.canHandle(message));
        }

        @Override
        public Object invoke(Object target, M message) {
            Optional<HandlerInvoker<M>> delegate =
                    methodHandlers.stream().filter(d -> d.canHandle(message)).findFirst();
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
            return invoker.canHandle(message);
        }

        @Override
        public Object invoke(M message) {
            return invoker.invoke(target, message);
        }
    }
}
