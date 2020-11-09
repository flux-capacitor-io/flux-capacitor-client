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

import io.fluxcapacitor.common.api.SerializedObject;

import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.stream.Stream;

public class AnnotatedUpcaster<T> {
    private final Method method;
    private final Upcast annotation;
    private final Function<SerializedObject<T, ?>, Stream<SerializedObject<T, ?>>> upcastFunction;

    public AnnotatedUpcaster(Method method,
                             Function<SerializedObject<T, ?>, Stream<SerializedObject<T, ?>>> upcastFunction) {
        this.method = method;
        this.annotation = method.getAnnotation(Upcast.class);
        this.upcastFunction = upcastFunction;
    }

    @SuppressWarnings("unchecked")
    public <S extends SerializedObject<T, S>> Stream<S> upcast(S input) {
        return annotation.type().equals(input.data().getType()) && annotation.revision() == input.data().getRevision()
                ? (Stream<S>) upcastFunction.apply(input) : Stream.of(input);
    }

    public Upcast getAnnotation() {
        return annotation;
    }

    @Override
    public String toString() {
        return method.toString();
    }
}
