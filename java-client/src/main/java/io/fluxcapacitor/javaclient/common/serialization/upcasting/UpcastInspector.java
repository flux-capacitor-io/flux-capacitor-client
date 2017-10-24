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

package io.fluxcapacitor.javaclient.common.serialization.upcasting;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class UpcastInspector {

    private static Comparator<AnnotatedUpcaster<?>> upcasterComparator =
            Comparator.<AnnotatedUpcaster<?>, Integer>comparing(u -> u.getAnnotation().revision())
                    .thenComparing(u -> u.getAnnotation().type());

    public static <T> List<AnnotatedUpcaster<T>> inspect(Collection<Object> upcasters, Class<T> dataType) {
        List<AnnotatedUpcaster<T>> result = new ArrayList<>();
        for (Object upcaster : upcasters) {
            for (Method method : upcaster.getClass().getMethods()) {
                if (method.isAnnotationPresent(Upcast.class)) {
                    result.add(createUpcaster(method, upcaster, dataType));
                }
            }
        }
        result.sort(upcasterComparator);
        return result;
    }

    private static <T> AnnotatedUpcaster<T> createUpcaster(Method method, Object target, Class<T> dataType) {
        if (method.getReturnType().equals(void.class)) {
            return new AnnotatedUpcaster<>(method, i -> Stream.empty());
        }
        Function<Data<T>, Object> invokeFunction = invokeFunction(method, target, dataType);
        Function<Object, Stream<Data<T>>> resultMapper = mapResult(method, dataType);
        return new AnnotatedUpcaster<>(method, d -> resultMapper.apply(invokeFunction.apply(d)));
    }

    private static <T> Function<Data<T>, Object> invokeFunction(Method method, Object target, Class<T> dataType) {
        Type[] parameters = method.getGenericParameterTypes();
        if (parameters.length != 1) {
            throw new IllegalArgumentException(
                    String.format("Upcaster method '%s' has unexpected number of parameters. Expected 1 or 0.", method));
        }
        if (parameters[0] instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) parameters[0];
            if (parameterizedType.getRawType().equals(Data.class) && dataType
                    .isAssignableFrom((Class<?>) parameterizedType.getActualTypeArguments()[0])) {
                return data -> invokeMethod(method, data, target);
            }
            if (dataType.isAssignableFrom((Class<?>) parameterizedType.getRawType())) {
                return data -> invokeMethod(method, data.getValue(), target);
            }
        } else if (dataType.isAssignableFrom((Class<?>) parameters[0])) {
            return data -> invokeMethod(method, data.getValue(), target);
        }
        throw new IllegalArgumentException(String.format(
                "First parameter in upcaster method '%s' is of unexpected type. Expected Data<%s> or %s.",
                method, dataType.getName(), dataType.getName()));
    }

    private static Object invokeMethod(Method method, Object argument, Object target) {
        try {
            return method.invoke(target, argument);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Not allowed to invoke method: " + method, e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Exception while upcasting using method: " + method, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Function<Object, Stream<Data<T>>> mapResult(Method method, Class<T> dataType) {
        if (dataType.isAssignableFrom(method.getReturnType())) {
            Upcast annotation = method.getAnnotation(Upcast.class);
            return r -> Stream.of(new Data<>((T) r, annotation.type(), annotation.revision() + 1));
        }
        if (method.getReturnType().equals(Data.class)) {
            return r -> Stream.of((Data<T>) r);
        }
        if (method.getReturnType().equals(Optional.class)) {
            ParameterizedType parameterizedType = (ParameterizedType) method.getGenericReturnType();
            if (parameterizedType.getActualTypeArguments()[0] instanceof Class<?>) {
                Class<?> typeParameter = (Class<?>) parameterizedType.getActualTypeArguments()[0];
                if (dataType.isAssignableFrom(typeParameter)) {
                    Upcast annotation = method.getAnnotation(Upcast.class);
                    return r -> ((Optional<T>) r)
                            .map(d -> Stream.of(new Data<>(d, annotation.type(), annotation.revision() + 1)))
                            .orElse(Stream.empty());
                }
            }
            else if (parameterizedType.getActualTypeArguments()[0] instanceof ParameterizedType) {
                if (((ParameterizedType) parameterizedType.getActualTypeArguments()[0]).getRawType().equals(Data.class)) {
                    return r -> ((Optional<Data<T>>) r).map(Stream::of).orElse(Stream.empty());
                }
            }
        }
        if (method.getReturnType().equals(Stream.class)) {
            return r -> (Stream<Data<T>>) r;
        }
        throw new IllegalStateException(String.format(
                "Unexpected return type of upcaster method '%s'. Expected Data<%s>, %s, Optional<Data<%s>>, Optional<%s>, Stream<Data<%s>> or void",
                method, dataType.getName(), dataType.getName(), dataType.getName(), dataType.getName(),
                dataType.getName()));
    }
}
