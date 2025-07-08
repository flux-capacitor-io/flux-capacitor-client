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

package io.fluxcapacitor.common.api.search;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.search.DefaultDocumentSerializer;
import io.fluxcapacitor.common.search.Document;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.With;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

/**
 * Represents a serialized form of a search document stored in a Flux Capacitor collection.
 * <p>
 * A {@code SerializedDocument} contains all metadata and content necessary to index, search, or retrieve a document.
 * It may encapsulate the document in two interchangeable forms:
 * <ul>
 *     <li>A lazily evaluated {@link Data} blob for serialized storage and transmission</li>
 *     <li>A lazily evaluated deserialized {@link Document} instance for programmatic access</li>
 * </ul>
 * Exactly one of {@code data} or {@code document} must be supplied during construction; the other will be lazily
 * derived and memoized as needed.
 *
 * @see Document
 */
@Value
@Builder(toBuilder = true)
public class SerializedDocument {

    /**
     * Unique identifier for this document within the collection.
     */
    String id;

    /**
     * Start timestamp (in epoch millis) representing when the document becomes valid.
     */
    Long timestamp;

    /**
     * End timestamp (in epoch millis) representing when the document expires or ends.
     */
    Long end;

    /**
     * Name of the document collection to which this document belongs.
     */
    String collection;

    @Getter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @With
    Supplier<Data<byte[]>> data;

    @Getter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    Supplier<Document> document;

    /**
     * Optional short summary of the document, e.g., for display or search previews.
     */
    String summary;

    /**
     * Structured facet entries associated with this document, used for filtering or grouping in queries.
     */
    Set<FacetEntry> facets;

    /**
     * Structured sortable entries used for sorting or filtering.
     */
    Set<SortableEntry> indexes;

    /**
     * Constructs a new instance of the SerializedDocument class with the specified parameters.
     *
     * @param id          the unique identifier of the document
     * @param timestamp   the creation timestamp of the document, in milliseconds since epoch
     * @param end         the end timestamp of the document, in milliseconds since epoch, or null if not applicable
     * @param collection  the name of the collection to which the document belongs
     * @param document    the serialized data representing the document
     * @param summary     a brief summary or description of the document
     * @param facets      a set of {@link FacetEntry} objects, representing facet fields and values for searchability
     * @param indexes     a set of {@link SortableEntry} objects, representing fields for sorting and fast range querying
     */
    @ConstructorProperties({"id", "timestamp", "end", "collection", "document", "summary", "facets", "indexes"})
    public SerializedDocument(String id, Long timestamp, Long end, String collection, Data<byte[]> document,
                              String summary, Set<FacetEntry> facets, Set<SortableEntry> indexes) {
        this(id, timestamp, end, collection, () -> document, null, summary, facets, indexes);
    }

    /**
     * Constructs a {@code SerializedDocument} from a deserialized {@link Document} representation.
     * Automatically extracts and converts its metadata.
     */
    public SerializedDocument(Document document) {
        this(document.getId(), Optional.ofNullable(document.getTimestamp()).map(Instant::toEpochMilli).orElse(null),
             Optional.ofNullable(document.getEnd()).map(Instant::toEpochMilli).orElse(null),
             document.getCollection(), null, () -> document,
             Optional.ofNullable(document.getSummary()).map(Supplier::get).orElse(null), document.getFacets(),
             document.getSortables());
    }

    @SuppressWarnings("unused")
    private SerializedDocument(String id, Long timestamp, Long end, String collection, Supplier<Data<byte[]>> data,
                               Supplier<Document> document, String summary, Set<FacetEntry> facets,
                               Set<SortableEntry> indexes) {
        if (data == null && document == null) {
            throw new IllegalStateException("Either the serialized data or deserialized document should be supplied");
        }
        this.id = id;
        this.timestamp = timestamp;
        this.end = end;
        this.collection = collection;
        this.data = data == null ? memoize(() -> DefaultDocumentSerializer.INSTANCE.serialize(document.get())) : data;
        this.document = document == null
                ? memoize(() -> {
            Data<byte[]> d = data.get();
            return DefaultDocumentSerializer.INSTANCE.canDeserialize(d)
                    ? DefaultDocumentSerializer.INSTANCE.deserialize(d).toBuilder().facets(facets).sortables(indexes).build()
                    : new Document(id, d.getType(), d.getRevision(), collection,
                                   Optional.ofNullable(timestamp).map(Instant::ofEpochMilli).orElse(null),
                                   Optional.ofNullable(end).map(Instant::ofEpochMilli).orElse(null),
                                   Collections.emptyMap(), () -> summary, facets, indexes);
        }) : document;
        this.summary = summary;
        this.facets = facets;
        this.indexes = indexes;
    }

    /**
     * Returns the adjusted end timestamp. If the end is null or invalid (i.e., before the start), the timestamp
     * is returned instead.
     */
    public Long getEnd() {
        return end == null || timestamp == null || end > timestamp ? end : timestamp;
    }

    /**
     * Returns the serialized representation of the document.
     */
    public Data<byte[]> getDocument() {
        return data.get();
    }

    /**
     * Returns the number of bytes in the serialized representation of the document.
     */
    public int bytes() {
        byte[] value = getDocument().getValue();
        return value == null ? 0 : value.length;
    }

    /**
     * Returns the deserialized document view.
     */
    public Document deserializeDocument() {
        return document.get();
    }
}
