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

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.search.SerializedDocument;

import java.time.Instant;

/**
 * Interface for converting domain objects to and from {@link SerializedDocument} instances for indexing and searching.
 * <p>
 * A {@code DocumentSerializer} defines how objects are transformed into a serializable structure suitable for Flux
 * Capacitor's search infrastructure. Documents are associated with:
 * <ul>
 *     <li>A unique ID (within a collection)</li>
 *     <li>A collection name that groups similar documents</li>
 *     <li>An optional timestamp indicating the start time of the document (for time-bounded documents)</li>
 *     <li>An optional end timestamp (for time-bounded documents)</li>
 *     <li>Optional metadata associated with the document</li>
 * </ul>
 * <p>
 * Implementations are typically used by the {@link DocumentStore} when indexing or retrieving documents.
 *
 * <p>
 * This interface supports both serialization ({@link #toDocument}) and deserialization ({@link #fromDocument}).
 *
 * @see DocumentStore
 * @see IndexOperation
 * @see SerializedDocument
 */
public interface DocumentSerializer {

    /**
     * Serializes a given value into a {@link SerializedDocument}, using the specified identifiers and timestamps.
     * <p>
     * This is a convenience method that uses empty {@link Metadata}.
     *
     * @param value      the value to be serialized
     * @param id         the unique identifier of the document (within the collection)
     * @param collection the name of the document collection
     * @param timestamp  the optional timestamp marking the start of the document's relevance
     * @param end        optional timestamp marking the end of the document's relevance
     * @return a {@code SerializedDocument} representing the given value
     */
    default SerializedDocument toDocument(Object value, String id, String collection, Instant timestamp, Instant end) {
        return toDocument(value, id, collection, timestamp, end, Metadata.empty());
    }

    /**
     * Serializes a given value into a {@link SerializedDocument}, using the specified identifiers, timestamps, and
     * metadata.
     *
     * @param value      the value to be serialized
     * @param id         the unique identifier of the document (within the collection)
     * @param collection the name of the document collection
     * @param timestamp  the timestamp marking the start of the document's relevance
     * @param end        optional timestamp marking the end of the document's relevance
     * @param metadata   additional metadata to include with the document
     * @return a {@code SerializedDocument} representing the given value
     */
    SerializedDocument toDocument(Object value, String id, String collection, Instant timestamp, Instant end,
                                  Metadata metadata);

    /**
     * Deserializes the payload of the given document into a Java object using the type information contained in the
     * document.
     *
     * @param document the {@link SerializedDocument} to deserialize
     * @param <T>      the target type
     * @return the deserialized object
     */
    <T> T fromDocument(SerializedDocument document);

    /**
     * Deserializes the payload of the given document into an instance of the specified type.
     *
     * @param document the {@link SerializedDocument} to deserialize
     * @param type     the target class for deserialization
     * @param <T>      the target type
     * @return the deserialized object
     */
    <T> T fromDocument(SerializedDocument document, Class<T> type);
}
