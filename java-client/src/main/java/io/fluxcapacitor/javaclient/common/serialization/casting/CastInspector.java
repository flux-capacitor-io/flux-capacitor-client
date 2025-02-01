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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.ensureAccessible;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAllMethods;

public class CastInspector {

    public static boolean hasCasterMethods(Class<?> type) {
        return getAllMethods(type).stream().anyMatch(m -> ReflectionUtils.has(Cast.class, m));
    }

    public static <T> List<AnnotatedCaster<T>> getCasters(Class<? extends Annotation> castAnnotation,
                                                          Collection<?> candidateTargets, Class<T> dataType,
                                                          Comparator<AnnotatedCaster<?>> casterComparator) {
        List<AnnotatedCaster<T>> result = new ArrayList<>();
        for (Object caster : candidateTargets) {
            getAllMethods(caster.getClass()).forEach(m -> createCaster(caster, m, dataType, castAnnotation)
                    .ifPresent(result::add));
        }
        result.sort(casterComparator);
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
        Function<SerializedObject<T, ?>, Object> invokeFunction = invokeFunction(method, target, dataType);
        BiFunction<SerializedObject<T, ?>, Supplier<Object>, Stream<SerializedObject<T, ?>>> resultMapper =
                mapResult(castParameters, method, dataType);
        return new AnnotatedCaster<>(method, castParameters, d -> resultMapper.apply(d, () -> invokeFunction.apply(d)));
    }

    private static <T> Function<SerializedObject<T, ?>, Object> invokeFunction(Method method, Object target,
                                                                               Class<T> dataType) {
        var parameterFunctions =
                Arrays.stream(method.getGenericParameterTypes()).<Function<SerializedObject<T, ?>, ?>>map(pt -> {
                    if (pt instanceof ParameterizedType parameterizedType) {
                        if (parameterizedType.getRawType().equals(Data.class) && dataType
                                .isAssignableFrom((Class<?>) parameterizedType.getActualTypeArguments()[0])) {
                            return SerializedObject::data;
                        }
                        if (dataType.isAssignableFrom((Class<?>) parameterizedType.getRawType())) {
                            return s -> s.data().getValue();
                        }
                    } else if (pt instanceof Class<?> c) {
                        if (dataType.isAssignableFrom(c)) {
                            return s -> s.data().getValue();
                        }
                        if (SerializedMessage.class.isAssignableFrom(c)) {
                            return s -> s instanceof HasSource<?> i
                                        && i.getSource() instanceof SerializedMessage m ? m : null;
                        }
                    }
                    throw new DeserializationException(String.format(
                            "Parameter in upcaster method '%s' is of unexpected type. Expected Data<%s> or %s.",
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
            throw new DeserializationException("Exception while upcasting using method: " + invoker.getMember(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> BiFunction<SerializedObject<T, ?>, Supplier<Object>, Stream<SerializedObject<T, ?>>> mapResult(
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
                "Unexpected return type of upcaster method '%s'. Expected Data<%s>, %s, Optional<Data<%s>>, Optional<%s>, Stream<Data<%s>> or void",
                method, dataType.getName(), dataType.getName(), dataType.getName(), dataType.getName(),
                dataType.getName()));
    }

}
