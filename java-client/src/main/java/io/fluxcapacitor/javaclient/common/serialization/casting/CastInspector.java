/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializationException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
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
        Type[] parameters = method.getGenericParameterTypes();
        if (parameters.length > 1) {
            throw new DeserializationException(
                    String.format("Upcaster method '%s' has unexpected number of parameters. Expected 1 or 0.",
                                  method));
        }
        if (parameters.length == 0) {
            return s -> invokeMethod(method, null, target);
        }
        if (parameters[0] instanceof ParameterizedType parameterizedType) {
            if (parameterizedType.getRawType().equals(Data.class) && dataType
                    .isAssignableFrom((Class<?>) parameterizedType.getActualTypeArguments()[0])) {
                return s -> invokeMethod(method, s.data(), target);
            }
            if (dataType.isAssignableFrom((Class<?>) parameterizedType.getRawType())) {
                return s -> invokeMethod(method, s.data().getValue(), target);
            }
        } else if (dataType.isAssignableFrom((Class<?>) parameters[0])) {
            return s -> invokeMethod(method, s.data().getValue(), target);
        }
        throw new DeserializationException(String.format(
                "First parameter in upcaster method '%s' is of unexpected type. Expected Data<%s> or %s.",
                method, dataType.getName(), dataType.getName()));
    }

    private static Object invokeMethod(Method method, Object argument, Object target) {
        try {
            return argument == null ? method.invoke(target) : method.invoke(target, argument);
        } catch (IllegalAccessException e) {
            throw new DeserializationException("Not allowed to invoke method: " + method, e);
        } catch (InvocationTargetException e) {
            throw new DeserializationException("Exception while upcasting using method: " + method, e);
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
            return (s, o) -> Stream.of(s.withData((Data<T>) o.get()));
        }
        if (method.getReturnType().equals(Optional.class)) {
            ParameterizedType parameterizedType = (ParameterizedType) method.getGenericReturnType();
            if (parameterizedType.getActualTypeArguments()[0] instanceof Class<?> typeParameter) {
                if (dataType.isAssignableFrom(typeParameter)) {
                    return (s, o) -> {
                        Optional<T> result = (Optional<T>) o.get();
                        return result.stream().map(t -> s.withData(new Data<>(
                                t, annotation.type(), annotation.revision() + annotation.revisionDelta(),
                                s.data().getFormat())));
                    };
                }
            } else if (parameterizedType.getActualTypeArguments()[0] instanceof ParameterizedType) {
                if (((ParameterizedType) parameterizedType.getActualTypeArguments()[0]).getRawType()
                        .equals(Data.class)) {
                    return (s, o) -> ((Optional<Data<T>>) o.get()).stream().map(s::withData);
                }
            }
        }
        if (method.getReturnType().equals(Stream.class)) {
            return (s, o) -> ((Stream<Data<T>>) o.get()).map(s::withData);
        }

        throw new DeserializationException(String.format(
                "Unexpected return type of upcaster method '%s'. Expected Data<%s>, %s, Optional<Data<%s>>, Optional<%s>, Stream<Data<%s>> or void",
                method, dataType.getName(), dataType.getName(), dataType.getName(), dataType.getName(),
                dataType.getName()));
    }

}
