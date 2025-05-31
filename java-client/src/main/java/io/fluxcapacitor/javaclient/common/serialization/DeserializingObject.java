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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.MemoizingFunction;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;

import java.lang.reflect.Type;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

/**
 * A wrapper around a {@link SerializedObject} that supports lazy deserialization of its payload.
 * <p>
 * This class defers the deserialization of the underlying object until it is first accessed via {@link #getPayload()}
 * or {@link #getPayloadAs(Type)}. The result is then memoized to avoid repeated deserialization.
 *
 * <p>
 * This is typically used for messages that may not require full deserialization (e.g. for routing or filtering). It is
 * extended by {@link DeserializingMessage} which adds additional context and metadata.
 *
 * @param <T> The raw data type of the serialized object (e.g., String or byte[])
 * @param <S> The specific {@link SerializedObject} type used to represent the serialized payload
 */
@ToString(exclude = "objectFunction")
public class DeserializingObject<T, S extends SerializedObject<T>> {
    /**
     * Returns the underlying {@link SerializedObject}.
     */
    @Getter
    private final S serializedObject;
    private final MemoizingFunction<Type, Object> objectFunction;

    /**
     * Returns the memoized deserialization function used to deserialize the payload. This method is protected for use
     * in subclasses.
     */
    protected MemoizingFunction<Type, Object> getObjectFunction() {
        return objectFunction;
    }

    /**
     * Creates a new {@code DeserializingObject} with the given serialized representation and deserializer function.
     *
     * @param serializedObject The raw serialized object
     * @param payload          A function to deserialize the object when needed
     */
    public DeserializingObject(S serializedObject, Function<Type, Object> payload) {
        this.serializedObject = serializedObject;
        this.objectFunction = memoize(payload);
    }

    /**
     * Returns the deserialized payload using the default target type ({@code Object.class}). The result is cached and
     * reused on subsequent calls.
     */
    @SuppressWarnings("unchecked")
    public <V> V getPayload() {
        return (V) objectFunction.apply(Object.class);
    }

    /**
     * Returns the deserialized payload using the specified target {@link Type}. Results are cached per type.
     *
     * @param type the desired target type
     * @param <V>  the expected type of the deserialized object
     */
    @SuppressWarnings("unchecked")
    public <V> V getPayloadAs(Type type) {
        return (V) objectFunction.apply(type);
    }

    /**
     * Returns {@code true} if the payload has already been deserialized using the default type.
     */
    public boolean isDeserialized() {
        return objectFunction.isCached(Object.class);
    }

    /**
     * Returns the declared type of the payload (e.g., fully qualified class name), or {@code null} if unknown.
     */
    public String getType() {
        return serializedObject.data().getType();
    }

    /**
     * Returns the revision number of the serialized payload, if available.
     */
    public int getRevision() {
        return serializedObject.data().getRevision();
    }

    /**
     * Attempts to resolve the declared payload class using {@link ReflectionUtils#classForName(String)} and throws an
     * exception if the class cannot be found.
     *
     * @return the {@link Class} corresponding to the declared type, or {@code null} if not resolvable
     */
    @SneakyThrows
    @SuppressWarnings("unused")
    public Class<?> getPayloadClass() {
        String type = getType();
        return type == null ? null : ReflectionUtils.classForName(type);
    }
}