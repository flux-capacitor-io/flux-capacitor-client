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

package io.fluxcapacitor.javaclient.common.serialization.casting;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.reflection.DefaultMemberInvoker;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializationException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.ensureAccessible;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAllMethods;

/**
 * Internal utility for inspecting and instantiating caster methods based on annotations such as {@link Upcast} or {@link Downcast}.
 *
 * <p>This class is used by {@link DefaultCasterChain} to discover methods annotated for casting purposes and wrap
 * them into {@link AnnotatedCaster} instances.
 *
 * <p>Supports a variety of method signatures for flexibility, including those returning {@code Data<T>}, {@code Optional<Data<T>>},
 * plain {@code T}, and {@code Stream<Data<T>>}. The inputs may include {@code Data<T>}, {@code T}, or {@code SerializedMessage}.
 *
 * <p>Not intended for public use.
 */
public class CastInspector {

    /**
     * Returns {@code true} if the given class contains any method annotated with {@link Cast}.
     *
     * @param type the class to inspect
     * @return true if at least one casting method is present, false otherwise
     */
    public static boolean hasCasterMethods(Class<?> type) {
        return getAllMethods(type).stream().anyMatch(m -> ReflectionUtils.has(Cast.class, m));
    }

    /**
     * Discovers all caster methods annotated with the given annotation (typically {@link Upcast} or {@link Downcast})
     * in the provided candidates, and wraps them as {@link AnnotatedCaster} instances.
     *
     * @param castAnnotation the annotation to look for
     * @param candidateTargets a collection of objects or classes that may define caster methods
     * @param dataType the expected input/output data type for casting
     * @param <T> the type of the serialized data being cast
     * @return a list of discovered annotated casters
     */
    public static <T> List<AnnotatedCaster<T>> getCasters(Class<? extends Annotation> castAnnotation,
                                                          Collection<?> candidateTargets, Class<T> dataType) {
        List<AnnotatedCaster<T>> result = new ArrayList<>();
        for (Object caster : candidateTargets) {
            var casterInstance = ReflectionUtils.asInstance(caster);
            getAllMethods(casterInstance.getClass()).forEach(
                    m -> createCaster(casterInstance, m, dataType, castAnnotation)
                            .ifPresent(result::add));
        }
        return result;
    }

    private static <T> Optional<AnnotatedCaster<T>> createCaster(Object target, Method m, Class<T> dataType,
                                                                 Class<? extends Annotation> castAnnotation) {
        if (!ReflectionUtils.has(castAnnotation, m)) {
            return Optional.empty();
        }
        return ReflectionUtils.getAnnotationAs(m, Cast.class, CastParameters.class).map(
                params -> createCaster(params, m, target, dataType));
    }

    private static <T> AnnotatedCaster<T> createCaster(CastParameters castParameters, Method method, Object target,
                                                       Class<T> dataType) {
        if (ensureAccessible(method).getReturnType().equals(void.class)) {
            return new AnnotatedCaster<>(method, castParameters, i -> Stream.empty());
        }
        Function<SerializedObject<T>, Object> invokeFunction = invokeFunction(method, target, dataType);
        BiFunction<SerializedObject<T>, Supplier<Object>, Stream<SerializedObject<T>>> resultMapper =
                mapResult(castParameters, method, dataType);
        return new AnnotatedCaster<>(method, castParameters, d -> resultMapper.apply(d, () -> invokeFunction.apply(d)));
    }

    private static <T> Function<SerializedObject<T>, Object> invokeFunction(Method method, Object target,
                                                                            Class<T> dataType) {
        var parameterFunctions =
                Arrays.stream(method.getGenericParameterTypes()).<Function<SerializedObject<T>, ?>>map(type -> {
                    switch (type) {
                        case ParameterizedType pt -> {
                            Type actualTypeArgument;
                            if (pt.getRawType().equals(Data.class)) {
                                Type[] actualTypeArguments = pt.getActualTypeArguments();
                                if (actualTypeArguments.length == 0) {
                                    return SerializedObject::data;
                                }
                                actualTypeArgument = actualTypeArguments[0];
                                if (actualTypeArgument instanceof WildcardType wt) {
                                    Type[] upperBounds = wt.getUpperBounds();
                                    if (upperBounds.length == 0) {
                                        return SerializedObject::data;
                                    }
                                    actualTypeArgument = upperBounds[0];
                                }
                            } else {
                                actualTypeArgument = pt.getRawType();
                            }
                            if (actualTypeArgument instanceof Class<?> c && (dataType.isAssignableFrom(c) || c.equals(
                                    Object.class))) {
                                return SerializedObject::data;
                            }
                        }
                        case Class<?> c -> {
                            if (dataType.isAssignableFrom(c)) {
                                return s -> s.data().getValue();
                            }
                            if (SerializedMessage.class.isAssignableFrom(c)) {
                                return s -> s instanceof HasSource<?> i
                                            && i.getSource() instanceof SerializedMessage m ? m : null;
                            }
                        }
                        case null, default -> {
                        }
                    }
                    throw new DeserializationException(String.format(
                            "Parameter in caster method '%s' is of unexpected type. Expected Data<%s> or %s.",
                            method, dataType.getName(), dataType.getName()));
                }).toList();
        var invoker = DefaultMemberInvoker.asInvoker(method);
        try {
            return s -> {
                Object[] args = new Object[parameterFunctions.size()];
                for (int i = 0; i < parameterFunctions.size(); i++) {
                    var arg = parameterFunctions.get(i).apply(s);
                    if (arg == null) {
                        return null;
                    }
                    args[i] = arg;
                }
                return invoker.invoke(target, args);
            };
        } catch (Throwable e) {
            throw new DeserializationException("Exception while casting using method: " + invoker.getMember(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> BiFunction<SerializedObject<T>, Supplier<Object>, Stream<SerializedObject<T>>> mapResult(
            CastParameters annotation, Method method, Class<T> dataType) {
        if (dataType.isAssignableFrom(method.getReturnType())) {
            return (s, o) -> Stream
                    .of(s.withData(new Data<>((Supplier<T>) o, annotation.type(), annotation.revision()
                                                                                  + annotation.revisionDelta(),
                                              s.data().getFormat())));
        }
        if (method.getReturnType().equals(Data.class)) {
            return (s, o) -> Optional.ofNullable((Data<T>) o.get()).stream().map(s::withData);
        }
        if (method.getReturnType().equals(Optional.class)) {
            ParameterizedType parameterizedType = (ParameterizedType) method.getGenericReturnType();
            if (parameterizedType.getActualTypeArguments()[0] instanceof Class<?> typeParameter) {
                if (dataType.isAssignableFrom(typeParameter)) {
                    return (s, o) -> Stream.of(s.withData(new Data<>(
                            () -> o.get() instanceof Optional<?> optional && optional.isPresent()
                                    ? (T) optional.get() : null,
                            annotation.type(), annotation.revision() + annotation.revisionDelta(),
                            s.data().getFormat())));
                }
            } else if (parameterizedType.getActualTypeArguments()[0] instanceof ParameterizedType) {
                if (((ParameterizedType) parameterizedType.getActualTypeArguments()[0]).getRawType()
                        .equals(Data.class)) {
                    return (s, o) -> o.get() instanceof Optional<?> optional && optional.isPresent()
                            ? ((Optional<Data<T>>) optional).stream().map(s::withData) : Stream.empty();
                }
            }
        }
        if (method.getReturnType().equals(Stream.class)) {
            return (s, o) -> o.get() instanceof Stream<?> stream ? ((Stream<Data<T>>) stream).map(s::withData)
                    : Stream.empty();
        }

        throw new DeserializationException(String.format(
                "Unexpected return type of caster method '%s'. Expected Data<%s>, %s, Optional<Data<%s>>, Optional<%s>, Stream<Data<%s>> or void",
                method, dataType.getName(), dataType.getName(), dataType.getName(), dataType.getName(),
                dataType.getName()));
    }

}
