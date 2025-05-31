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

/**
 * Represents a single result returned by a {@link Search} query.
 * <p>
 * A {@code SearchHit} encapsulates a document retrieved from the search index, along with metadata
 * such as its unique ID, collection name, and optional timestamp and end time. The actual document value
 * is lazily supplied via a {@link Supplier}, enabling deferred deserialization or transformation.
 *
 * <p>This class implements the {@link Entry} interface and is used extensively in streaming search operations,
 * particularly via {@link Search#streamHits()} or {@link Search#streamHits(int)}.
 *
 * @param <T> The type of the deserialized document value.
 *
 * @see Search
 * @see Search#streamHits()
 * @see SerializedDocument
 */
@Value
public class SearchHit<T> implements Entry<T> {

    /**
     * Constructs a {@code SearchHit} instance from a raw {@link SerializedDocument}.
     * <p>
     * This is useful when dealing with lower-level deserialization logic or constructing hits manually
     * in test environments or in-memory stores.
     *
     * @param document The serialized document from which to construct the search hit.
     * @return A new {@code SearchHit} instance wrapping the serialized document.
     */
    public static SearchHit<SerializedDocument> fromDocument(SerializedDocument document) {
        return new SearchHit<>(document.getId(), document.getCollection(),
                               document.getTimestamp() == null ? null : Instant.ofEpochMilli(document.getTimestamp()),
                               document.getEnd() == null ? null : Instant.ofEpochMilli(document.getEnd()),
                               () -> document);
    }

    /**
     * The document's unique identifier.
     */
    String id;

    /**
     * The collection the document belongs to.
     */
    String collection;

    /**
     * The document's associated start timestamp, if present.
     */
    Instant timestamp;

    /**
     * The document's end timestamp, if specified (e.g., for time-bounded entries).
     */
    Instant end;

    @Getter(AccessLevel.NONE)
    Supplier<T> valueSupplier;

    /**
     * Returns the actual value of the hit, deserialized or computed via the internal {@code valueSupplier}.
     *
     * @return The document value.
     */
    public T getValue() {
        return valueSupplier.get();
    }

    /**
     * Transforms the hit’s value using the provided mapper function, producing a new {@code SearchHit}
     * with the same metadata but with a new mapped value.
     *
     * @param mapper A function to convert the current value to another type.
     * @param <V>    The new value type.
     * @return A new {@code SearchHit} instance with the transformed value.
     */
    public <V> SearchHit<V> map(Function<T, V> mapper) {
        return new SearchHit<>(id, collection, timestamp, end, () -> mapper.apply(getValue()));
    }
}
