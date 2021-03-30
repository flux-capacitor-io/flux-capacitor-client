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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.api.SerializedObject;
import lombok.SneakyThrows;
import lombok.ToString;

import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

@ToString(exclude = "object")
public class DeserializingObject<T, S extends SerializedObject<T, S>> {
    private final S serializedObject;
    private final ObjectUtils.MemoizingFunction<Class<?>, Object> object;

    public DeserializingObject(S serializedObject, Function<Class<?>, Object> payload) {
        this.serializedObject = serializedObject;
        this.object = memoize(payload);
    }

    @SuppressWarnings("unchecked")
    public <V> V getPayload() {
        return (V) object.apply(Object.class);
    }

    @SuppressWarnings("unchecked")
    public <V> V getPayloadAs(Class<V> type) {
        return (V) object.apply(type);
    }

    public boolean isDeserialized() {
        return object.isCached(Object.class);
    }

    public String getType() {
        return serializedObject.data().getType();
    }

    public int getRevision() {
        return serializedObject.data().getRevision();
    }

    public S getSerializedObject() {
        return serializedObject;
    }

    @SneakyThrows
    @SuppressWarnings("unused")
    public Class<?> getPayloadClass() {
        return Class.forName(getType());
    }
}