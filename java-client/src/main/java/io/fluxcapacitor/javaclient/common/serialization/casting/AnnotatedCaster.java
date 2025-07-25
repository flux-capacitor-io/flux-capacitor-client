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

import io.fluxcapacitor.common.api.SerializedObject;
import lombok.Getter;

import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Represents a dynamically discovered casting method (annotated with {@link Upcast} or {@link Downcast})
 * that is bound to a specific data revision and type.
 *
 * <p>This class delegates casting logic to a functional wrapper around the annotated method, and is
 * typically instantiated by {@link CastInspector} as part of building a {@link CasterChain}.
 *
 * @param <T> the underlying type of the serialized data
 */
public class AnnotatedCaster<T> {
    private final Method method;
    @Getter
    private final CastParameters parameters;
    private final Function<SerializedObject<T>, Stream<SerializedObject<T>>> castFunction;

    /**
     * Creates a new {@code AnnotatedCaster}.
     *
     * @param method the underlying Java method that performs casting
     * @param castParameters the parameters from the {@link Cast} annotation (e.g., type and revision)
     * @param castFunction a function that applies the casting logic to a given {@link SerializedObject}
     */
    public AnnotatedCaster(Method method, CastParameters castParameters,
                           Function<SerializedObject<T>, Stream<SerializedObject<T>>> castFunction) {
        this.method = method;
        this.parameters = castParameters;
        this.castFunction = castFunction;
    }

    /**
     * Applies the casting logic to the given serialized object if its type and revision match this caster’s parameters.
     * If the input does not match, it is returned unmodified.
     *
     * @param input the serialized object to potentially cast
     * @param <S> the input and output serialized object type
     * @return a stream containing the casted result(s) or the original input if no match was found
     */
    @SuppressWarnings("unchecked")
    public <S extends SerializedObject<T>> Stream<S> cast(S input) {
        return parameters.type().equals(input.data().getType()) && parameters.revision() == input.data().getRevision()
                ? (Stream<S>) castFunction.apply(input) : Stream.of(input);
    }

    @Override
    public String toString() {
        return method.toString();
    }
}
