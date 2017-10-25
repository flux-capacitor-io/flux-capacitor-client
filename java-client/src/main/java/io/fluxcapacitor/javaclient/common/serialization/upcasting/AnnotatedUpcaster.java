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

import io.fluxcapacitor.common.api.Data;

import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.stream.Stream;

public class AnnotatedUpcaster<T> {
    private final Method method;
    private final Upcast annotation;
    private final Function<Data<T>, Stream<Data<T>>> upcastFunction;

    public AnnotatedUpcaster(Method method,
                             Function<Data<T>, Stream<Data<T>>> upcastFunction) {
        this.method = method;
        this.annotation = method.getAnnotation(Upcast.class);
        this.upcastFunction = upcastFunction;
    }

    public Stream<Data<T>> upcast(Data<T> input) {
        return annotation.type().equals(input.getType()) && annotation.revision() == input.getRevision() ?
                upcastFunction.apply(input) : Stream.of(input);
    }

    public Upcast getAnnotation() {
        return annotation;
    }

    @Override
    public String toString() {
        return method.toString();
    }
}
