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

package io.fluxcapacitor.javaclient.persisting.search;

import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.javaclient.common.Entry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;

import java.time.Instant;
import java.util.function.Function;
import java.util.function.Supplier;

@Value
public class SearchHit<T> implements Entry<T> {

    public static SearchHit<SerializedDocument> fromDocument(SerializedDocument document) {
        return new SearchHit<>(document.getId(), document.getCollection(),
                               document.getTimestamp() == null ? null : Instant.ofEpochMilli(document.getTimestamp()),
                               document.getEnd() == null ? null : Instant.ofEpochMilli(document.getEnd()),
                               () -> document);
    }

    String id;
    String collection;
    Instant timestamp;
    Instant end;
    @Getter(AccessLevel.NONE)
    Supplier<T> valueSupplier;

    public T getValue() {
        return valueSupplier.get();
    }

    public <V> SearchHit<V> map(Function<T, V> mapper) {
        return new SearchHit<>(id, collection, timestamp, end, () -> mapper.apply(getValue()));
    }
}
