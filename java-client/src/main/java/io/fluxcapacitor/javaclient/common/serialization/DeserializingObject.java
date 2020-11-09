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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.ObjectUtils.MemoizingSupplier;
import io.fluxcapacitor.common.api.SerializedObject;
import lombok.SneakyThrows;
import lombok.ToString;

import java.util.function.Supplier;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

@ToString(exclude = "object")
public class DeserializingObject<T, S extends SerializedObject<T, S>> {
    private final S serializedObject;
    private final MemoizingSupplier<Object> object;

    public DeserializingObject(S serializedObject, Supplier<Object> payload) {
        this.serializedObject = serializedObject;
        this.object = memoize(payload);
    }

    @SuppressWarnings("unchecked")
    public <V> V getPayload() {
        return (V) object.get();
    }

    public boolean isDeserialized() {
        return object.isCached();
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