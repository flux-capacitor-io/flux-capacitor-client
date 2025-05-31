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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Metadata;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Builder-style interface for indexing documents into the {@link DocumentStore}.
 * <p>
 * This interface allows step-by-step construction of an indexing operation, including setting document identifiers,
 * time ranges, collections, metadata, and whether to only index if the document doesn't already exist. Once configured,
 * the operation can be triggered using various convenience methods (e.g. {@link #index()}, {@link #indexAndForget()},
 * or {@link #indexAndWait()}).
 * <p>
 * To begin building an index operation, use:
 * <pre>
 *     FluxCapacitor.get().documentStore().prepareIndex(myObject)
 *         .id("myId")
 *         .collection("MyCollection")
 *         .timestamp(Instant.now())
 *         .addMetadata("source", "api")
 *         .indexAndWait();
 * </pre>
 *
 * @see DocumentStore#prepareIndex(Object)
 */
public interface IndexOperation {

    /**
     * Executes the indexing operation with a default guarantee of {@link Guarantee#STORED}.
     */
    default CompletableFuture<Void> index() {
        return index(Guarantee.STORED);
    }

    /**
     * Executes the indexing operation with a {@link Guarantee#NONE} guarantee and does not wait for completion.
     */
    @SneakyThrows
    default void indexAndForget() {
        index(Guarantee.NONE);
    }

    /**
     * Executes the indexing operation with {@link Guarantee#STORED} and blocks until it is completed.
     */
    @SneakyThrows
    default void indexAndWait() {
        indexAndWait(Guarantee.STORED);
    }

    /**
     * Executes the indexing operation with the specified guarantee and blocks until it is completed.
     */
    @SneakyThrows
    default void indexAndWait(Guarantee guarantee) {
        index(guarantee).get();
    }

    /**
     * Executes the indexing operation with the provided guarantee.
     */
    CompletableFuture<Void> index(Guarantee guarantee);

    /**
     * Sets the ID of the document to index.
     */
    IndexOperation id(@NonNull Object id);

    /**
     * Sets the collection into which the document should be indexed.
     */
    IndexOperation collection(@NonNull Object collection);

    /**
     * Sets the start time (timestamp) for the document.
     */
    IndexOperation start(Instant start);

    /**
     * Sets the end time for the document (e.g., used in range queries or versioning).
     */
    IndexOperation end(Instant end);

    /**
     * Sets both start and end time to the same instant (convenience method).
     */
    default IndexOperation timestamp(Instant timestamp) {
        return start(timestamp).end(timestamp);
    }

    /**
     * Sets the start and end time using a time range.
     */
    default IndexOperation period(Instant start, Instant end) {
        return start(start).end(end);
    }

    /**
     * Adds metadata to the index operation, merging with any previously set metadata.
     */
    default IndexOperation addMetadata(@NonNull Metadata metadata) {
        return metadata(metadata().with(metadata));
    }

    /**
     * Adds a single metadata key-value pair.
     */
    default IndexOperation addMetadata(@NonNull Object key, Object value) {
        return metadata(metadata().with(key, value));
    }

    /**
     * Adds multiple metadata entries from a map.
     */
    default IndexOperation addMetadata(@NonNull Map<String, ?> values) {
        return metadata(metadata().with(values));
    }

    /**
     * Replaces all metadata in this operation with the given metadata.
     */
    IndexOperation metadata(Metadata metadata);

    /**
     * If set to {@code true}, only index the document if it does not already exist.
     */
    IndexOperation ifNotExists(boolean toggle);

    /**
     * Returns the configured start timestamp for this operation.
     */
    Instant start();

    /**
     * Returns the configured end timestamp for this operation.
     */
    Instant end();

    /**
     * Returns the configured ID of the document.
     */
    Object id();

    /**
     * Returns the configured metadata.
     */
    Metadata metadata();

    /**
     * Whether the operation is configured to only index if the document doesn't already exist.
     */
    boolean ifNotExists();

    /**
     * Creates a deep copy of this operation, preserving all configured values.
     */
    IndexOperation copy();

}
