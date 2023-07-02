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

package io.fluxcapacitor.javaclient.persisting.search.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.search.CreateAuditTrail;
import io.fluxcapacitor.common.api.search.DocumentStats;
import io.fluxcapacitor.common.api.search.DocumentUpdate;
import io.fluxcapacitor.common.api.search.GetDocument;
import io.fluxcapacitor.common.api.search.GetSearchHistogram;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import io.fluxcapacitor.common.api.search.SearchHistogram;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.api.search.BulkUpdate.Type.indexIfNotExists;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class InMemorySearchClient implements SearchClient {
    private final List<Document> documents = new CopyOnWriteArrayList<>();

    @Override
    public synchronized Awaitable index(List<SerializedDocument> documents, Guarantee guarantee, boolean ifNotExists) {
        Function<Document, String> identify = d -> d.getCollection() + "/" + d.getId();
        Map<String, Document> existing = this.documents.stream().collect(toMap(identify, identity()));
        Map<String, Document> updates = documents.stream().map(SerializedDocument::deserializeDocument)
                .collect(toMap(identify, identity(), (a, b) -> b, LinkedHashMap::new));
        if (ifNotExists) {
            updates.entrySet().stream().filter(e -> !existing.containsKey(e.getKey()))
                    .forEach(e -> this.documents.add(e.getValue()));
        } else {
            updates.forEach((key, value) -> {
                Optional.ofNullable(existing.get(key)).ifPresent(this.documents::remove);
                this.documents.add(value);
            });
        }
        return Awaitable.ready();
    }

    @Override
    public Stream<SearchHit<SerializedDocument>> search(SearchDocuments searchDocuments, int fetchSize) {
        SearchQuery query = searchDocuments.getQuery();
        Stream<Document> documentStream = documents.stream().filter(query::matches);
        documentStream = documentStream.sorted(Document.createComparator(searchDocuments));
        if (!searchDocuments.getPathFilters().isEmpty()) {
            Predicate<Document.Path> pathFilter = searchDocuments.computePathFilter();
            documentStream = documentStream.map(d -> d.filterPaths(pathFilter));
        }
        if (searchDocuments.getSkip() > 0) {
            documentStream = documentStream.skip(searchDocuments.getSkip());
        }
        if (searchDocuments.getLastHit() != null) {
            documentStream = documentStream.dropWhile(d -> !d.getId().equals(searchDocuments.getLastHit().getId()))
                    .skip(1);
        }
        if (searchDocuments.getMaxSize() != null) {
            documentStream = documentStream.limit(searchDocuments.getMaxSize());
        }
        return documentStream
                .map(d -> new SearchHit<>(d.getId(), d.getCollection(), d.getTimestamp(), d.getEnd(),
                                          () -> new SerializedDocument(d)));
    }

    @Override
    public Optional<SerializedDocument> fetch(GetDocument r) {
        return documents.stream().filter(
                d -> Objects.equals(r.getId(), d.getId()) && Objects.equals(r.getCollection(), d.getCollection()))
                .findFirst().map(SerializedDocument::new);
    }

    @Override
    public Awaitable delete(SearchQuery query, Guarantee guarantee) {
        documents.removeAll(documents.stream().filter(query::matches).toList());
        return Awaitable.ready();
    }

    @Override
    public Awaitable delete(String documentId, String collection, Guarantee guarantee) {
        documents.removeIf(d -> Objects.equals(documentId, d.getId()) && Objects.equals(collection, d.getCollection()));
        return Awaitable.ready();
    }

    @Override
    public Awaitable createAuditTrail(CreateAuditTrail request) {
        return Awaitable.ready();
    }

    @Override
    public Awaitable deleteCollection(String collection, Guarantee guarantee) {
        documents.removeIf(d -> Objects.equals(collection, d.getCollection()));
        return Awaitable.ready();
    }

    @Override
    public List<DocumentStats> fetchStatistics(SearchQuery query, List<String> fields, List<String> groupBy) {
        return DocumentStats.compute(documents.stream().filter(query::matches), fields, groupBy);
    }

    @Override
    public SearchHistogram fetchHistogram(GetSearchHistogram request) {
        SearchQuery query = request.getQuery();
        List<Long> results = IntStream.range(0, request.getResolution()).mapToLong(i -> 0L).boxed().collect(toList());
        if (query.getSince() == null) {
            return new SearchHistogram(query.getSince(), query.getBefore(), results);
        }
        if (query.getBefore() == null) {
            query = query.toBuilder().before(Instant.now()).build();
        }
        long min = query.getSince().toEpochMilli();
        long delta = query.getBefore().toEpochMilli() - min;
        long step = Math.min(1, delta / request.getResolution());

        search(SearchDocuments.builder().query(query).build(), -1)
                .map(h -> h.getValue().deserializeDocument())
                .collect(groupingBy(d -> (d.getTimestamp().toEpochMilli() - min) / step))
                .forEach((bucket, hits) -> results.set(bucket.intValue(), (long) hits.size()));
        return new SearchHistogram(query.getSince(), query.getBefore(), results);
    }

    @Override
    public Awaitable bulkUpdate(Collection<DocumentUpdate> updates, Guarantee guarantee) {
        updates.forEach(action -> {
            switch (action.getType()) {
                case delete -> delete(action.getId(), action.getCollection(), guarantee);
                case index, indexIfNotExists -> index(List.of(action.getObject()), guarantee,
                                                      action.getType().equals(indexIfNotExists));
            }
        });
        return Awaitable.ready();
    }

    @Override
    public void close() {
    }
}
