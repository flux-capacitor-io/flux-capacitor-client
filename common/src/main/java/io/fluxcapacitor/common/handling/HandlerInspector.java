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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

public class HandlerInspector {

    public static <M> List<HandlerInvoker<M>> inspect(List<?> targets, Class<? extends Annotation> methodAnnotation,
                                                      List<ParameterResolver<M>> parameterResolvers) {
        return targets.stream().filter(o -> hasHandlerMethods(o, methodAnnotation))
                .map(o -> inspect(o, methodAnnotation, parameterResolvers)).collect(toList());
    }

    public static boolean hasHandlerMethods(Object target, Class<? extends Annotation> methodAnnotation) {
        return Arrays.stream(target.getClass().getMethods()).anyMatch(m -> m.isAnnotationPresent(methodAnnotation));
    }

    public static <M> HandlerInvoker<M> inspect(Object target, Class<? extends Annotation> methodAnnotation,
                                                List<ParameterResolver<M>> parameterResolvers) {
        return new ObjectHandlerInvoker<>(
                Arrays.stream(target.getClass().getMethods()).filter(m -> m.isAnnotationPresent(methodAnnotation))
                        .map(m -> new MethodHandlerInvoker<>(target, m, parameterResolvers))
                        .sorted(Comparator.naturalOrder())
                        .collect(toList()));
    }

    protected static class MethodHandlerInvoker<M> implements HandlerInvoker<M>, Comparable<MethodHandlerInvoker<M>> {

        private final Object target;
        private final Method method;
        private final List<Function<M, Object>> parameterSuppliers;
        private final Function<M, ? extends Class<?>> payloadTypeSupplier;

        protected MethodHandlerInvoker(Object target, Method method, List<ParameterResolver<M>> parameterResolvers) {
            this.target = target;
            this.method = method;
            this.parameterSuppliers = getParameterSuppliers(method, parameterResolvers);
            this.payloadTypeSupplier = getPayloadTypeSupplier(method, parameterResolvers);
        }

        @Override
        public boolean canHandle(M message) {
            return getPayloadType().isAssignableFrom(payloadTypeSupplier.apply(message));
        }

        @Override
        public Object invoke(M message) {
            try {
                return method.invoke(target, parameterSuppliers.stream().map(s -> s.apply(message)).toArray());
            } catch (InvocationTargetException e) {
                Exception thrown = e;
                if (e.getCause() instanceof Exception) {
                    thrown = (Exception) e.getCause();
                }
                throw new HandlerException(format("Target failed to handle a %s, method: %s", message, method), thrown);
            } catch (IllegalAccessException e) {
                throw new HandlerException(format("Failed to handle a %s, method: %s", message, method), e);
            }
        }

        protected List<Function<M, Object>> getParameterSuppliers(Method method,
                                                                  List<ParameterResolver<M>> resolvers) {
            if (method.getParameterCount() == 0) {
                throw new IllegalStateException("Annotated method should contain at least one parameter");
            }
            return Arrays.stream(method.getParameters())
                    .map(p -> resolvers.stream().map(r -> r.resolve(p)).filter(Objects::nonNull).findFirst()
                            .orElseThrow(() -> new IllegalStateException("Could not resolve parameter " + p)))
                    .collect(toList());
        }

        protected Function<M, ? extends Class<?>> getPayloadTypeSupplier(Method method,
                                                                         List<ParameterResolver<M>> resolvers) {
            Parameter parameter = method.getParameters()[0];
            return resolvers.stream().map(r -> r.resolveClass(parameter)).findFirst().orElseThrow(
                    () -> new IllegalStateException("Could not determine payload type for method " + method));
        }

        protected Class<?> getPayloadType() {
            return method.getParameterTypes()[0];
        }

        @Override
        @SuppressWarnings("NullableProblems")
        public int compareTo(MethodHandlerInvoker<M> o) {
            int result = comparePayloads(getPayloadType(), o.getPayloadType());
            if (result == 0) {
                result = method.toGenericString().compareTo(o.method.toGenericString());
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

    protected static class ObjectHandlerInvoker<M> implements HandlerInvoker<M> {
        private final List<HandlerInvoker<M>> methodHandlers;

        protected ObjectHandlerInvoker(List<? extends HandlerInvoker<M>> methodHandlers) {
            this.methodHandlers = new ArrayList<>(methodHandlers);
        }

        @Override
        public boolean canHandle(M message) {
            return methodHandlers.stream().anyMatch(h -> h.canHandle(message));
        }

        @Override
        public Object invoke(M message) {
            Optional<HandlerInvoker<M>> delegate =
                    methodHandlers.stream().filter(d -> d.canHandle(message)).findFirst();
            if (!delegate.isPresent()) {
                throw new IllegalArgumentException("No method found that could handle " + message);
            }
            return delegate.get().invoke(message);
        }
    }
}
