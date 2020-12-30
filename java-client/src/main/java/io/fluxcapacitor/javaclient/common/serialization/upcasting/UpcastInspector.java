/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.common.serialization.upcasting;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.ensureAccessible;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAllMethods;

public class UpcastInspector {

    private static final Comparator<AnnotatedUpcaster<?>> upcasterComparator =
            Comparator.<AnnotatedUpcaster<?>, Integer>comparing(u -> u.getAnnotation().revision())
                    .thenComparing(u -> u.getAnnotation().type());

    public static boolean hasAnnotatedMethods(Class<?> type) {
        return getAllMethods(type).stream().anyMatch(m -> m.isAnnotationPresent(Upcast.class));
    }

    public static <T> List<AnnotatedUpcaster<T>> inspect(Collection<?> upcasters, Converter<T> converter) {
        List<AnnotatedUpcaster<T>> result = new ArrayList<>();
        for (Object upcaster : upcasters) {
            getAllMethods(upcaster.getClass()).stream().filter(m -> m.isAnnotationPresent(Upcast.class))
                    .forEach(m -> result.add(createUpcaster(m, upcaster, converter)));
        }
        result.sort(upcasterComparator);
        return result;
    }

    private static <T> AnnotatedUpcaster<T> createUpcaster(Method method, Object target, Converter<T> converter) {
        if (method.getReturnType().equals(void.class)) {
            return new AnnotatedUpcaster<>(method, i -> Stream.empty());
        }
        method = ensureAccessible(method);
        Function<SerializedObject<T, ?>, Object> invokeFunction = invokeFunction(method, target, converter.getDataType());
        BiFunction<SerializedObject<T, ?>, Supplier<Object>, Stream<SerializedObject<T, ?>>> resultMapper =
                mapResult(method, converter);
        return new AnnotatedUpcaster<>(method, d -> resultMapper.apply(d, () -> invokeFunction.apply(d)));
    }

    private static <T> Function<SerializedObject<T, ?>, Object> invokeFunction(Method method, Object target,
                                                                               Class<T> dataType) {
        Type[] parameters = method.getGenericParameterTypes();
        if (parameters.length > 1) {
            throw new SerializationException(
                    String.format("Upcaster method '%s' has unexpected number of parameters. Expected 1 or 0.",
                            method));
        }
        if (parameters.length == 0) {
            return s -> invokeMethod(method, null, target);
        }
        if (parameters[0] instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) parameters[0];
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
        throw new SerializationException(String.format(
                "First parameter in upcaster method '%s' is of unexpected type. Expected Data<%s> or %s.",
                method, dataType.getName(), dataType.getName()));
    }

    private static Object invokeMethod(Method method, Object argument, Object target) {
        try {
            return argument==null? method.invoke(target) : method.invoke(target, argument);
        } catch (IllegalAccessException e) {
            throw new SerializationException("Not allowed to invoke method: " + method, e);
        } catch (InvocationTargetException e) {
            throw new SerializationException("Exception while upcasting using method: " + method, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> BiFunction<SerializedObject<T, ?>, Supplier<Object>, Stream<SerializedObject<T, ?>>> mapResult(
            Method method, Converter<T> converter) {
        Class<T> dataType = converter.getDataType();
        if (dataType.isAssignableFrom(method.getReturnType())) {
            Upcast annotation = method.getAnnotation(Upcast.class);
            return (s, o) -> Stream
                    .of(s.withData(new Data<>((Supplier<T>) o, annotation.type(), annotation.revision() + 1)));
        }
        if (converter.canApplyPatch(method.getReturnType())) {
            Upcast annotation = method.getAnnotation(Upcast.class);
            return (s, o) -> Stream
                    .of(s.withData(new Data<>((Supplier<T>) converter.applyPatch(s, o, method.getReturnType()),
                            annotation.type(), annotation.revision() + 1)));
        }
        if (method.getReturnType().equals(Data.class)) {
            return (s, o) -> Stream.of(s.withData((Data<T>) o.get()));
        }
        if (method.getReturnType().equals(Optional.class)) {
            ParameterizedType parameterizedType = (ParameterizedType) method.getGenericReturnType();
            if (parameterizedType.getActualTypeArguments()[0] instanceof Class<?>) {
                Class<?> typeParameter = (Class<?>) parameterizedType.getActualTypeArguments()[0];
                if (dataType.isAssignableFrom(typeParameter)) {
                    Upcast annotation = method.getAnnotation(Upcast.class);
                    return (s, o) -> {
                        Optional<T> result = (Optional<T>) o.get();
                        return result.<Stream<SerializedObject<T, ?>>>map(
                                t -> Stream.of(s.withData(new Data<>(t, annotation.type(), annotation.revision() + 1))))
                                .orElseGet(Stream::empty);
                    };
                }
            } else if (parameterizedType.getActualTypeArguments()[0] instanceof ParameterizedType) {
                if (((ParameterizedType) parameterizedType.getActualTypeArguments()[0]).getRawType()
                        .equals(Data.class)) {
                    return (s, o) -> ((Optional<Data<T>>) o.get())
                            .<Stream<SerializedObject<T, ?>>>map(d -> Stream.of(s.withData(d))).orElse(Stream.empty());
                }
            }
        }
        if (method.getReturnType().equals(Stream.class)) {
            return (s, o) -> ((Stream<Data<T>>) o.get()).map(s::withData);
        }

        throw new SerializationException(String.format(
                "Unexpected return type of upcaster method '%s'. Expected Data<%s>, %s, Optional<Data<%s>>, Optional<%s>, Stream<Data<%s>>%s or void",
                method, dataType.getName(), dataType.getName(), dataType.getName(), dataType.getName(),
                dataType.getName(), dataType.isAssignableFrom(JsonNode.class) ? ", JsonPatch" : ""));
    }

}
