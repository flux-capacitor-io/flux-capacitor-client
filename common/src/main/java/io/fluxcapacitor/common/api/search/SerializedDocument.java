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
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

@Value
@Builder(toBuilder = true)
public class SerializedDocument {
    String id;
    Long timestamp, end;
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
    String summary;
    Set<FacetEntry> facets;

    @ConstructorProperties({"id", "timestamp", "end", "collection", "document", "summary", "facets"})
    public SerializedDocument(String id, Long timestamp, Long end, String collection, Data<byte[]> document,
                              String summary, Set<FacetEntry> facets) {
        this(id, timestamp, end, collection, () -> document, null, summary, facets);
    }

    public SerializedDocument(Document document) {
        this(document.getId(), Optional.ofNullable(document.getTimestamp()).map(Instant::toEpochMilli).orElse(null),
             Optional.ofNullable(document.getEnd()).map(Instant::toEpochMilli).orElse(null),
             document.getCollection(), null, () -> document,
             Optional.ofNullable(document.getSummary()).map(Supplier::get).orElse(null), document.getFacets());
    }

    @SuppressWarnings("unused")
    private SerializedDocument(String id, Long timestamp, Long end, String collection, Supplier<Data<byte[]>> data,
                               Supplier<Document> document, String summary, Set<FacetEntry> facets) {
        if (data == null && document == null) {
            throw new IllegalStateException("Either the serialized data or deserialized document should be supplied");
        }
        this.id = id;
        this.timestamp = timestamp;
        this.end = end;
        this.collection = collection;
        this.data = data == null ? memoize(() -> DefaultDocumentSerializer.INSTANCE.serialize(document.get())) : data;
        this.document = document == null
                ? memoize(() -> DefaultDocumentSerializer.INSTANCE.deserialize(data.get())
                .toBuilder().facets(facets).build()) : document;
        this.summary = summary;
        this.facets = facets;
    }

    public Long getEnd() {
        return end == null || timestamp == null || end > timestamp ? end : timestamp;
    }

    public Data<byte[]> getDocument() {
        return data.get();
    }

    public Document deserializeDocument() {
        return document.get();
    }
}
