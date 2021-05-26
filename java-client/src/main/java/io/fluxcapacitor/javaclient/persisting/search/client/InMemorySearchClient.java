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

package io.fluxcapacitor.javaclient.persisting.search.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.search.*;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.search.Document.EntryType.NUMERIC;
import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

public class InMemorySearchClient implements SearchClient {
    private final List<Document> documents = new CopyOnWriteArrayList<>();

    @Override
    public synchronized Awaitable index(List<Document> documents, Guarantee guarantee, boolean ifNotExists) {
        Function<Document, String> identify = d -> d.getCollection() + "/" + d.getId();
        Map<String, Document> existing = this.documents.stream().collect(toMap(identify, identity()));
        Map<String, Document> updates = documents.stream().collect(toMap(identify, identity(), (a, b) -> b, LinkedHashMap::new));
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
    public Stream<SearchHit<Document>> search(SearchDocuments searchDocuments) {
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
        if (searchDocuments.getMaxSize() != null) {
            documentStream = documentStream.limit(searchDocuments.getMaxSize());
        }
        return documentStream
                .map(d -> new SearchHit<>(d.getId(), d.getCollection(), d.getTimestamp(), d.getEnd(), () -> d));
    }

    @Override
    public Awaitable delete(SearchQuery query, Guarantee guarantee) {
        documents.removeAll(documents.stream().filter(query::matches).collect(Collectors.toList()));
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
    public Awaitable deleteCollection(String collection) {
        documents.removeIf(d -> Objects.equals(collection, d.getCollection()));
        return Awaitable.ready();
    }

    @Override
    public List<DocumentStats> getStatistics(SearchQuery query, List<String> fields, List<String> groupBy) {
        if (fields.isEmpty()) {
            return emptyList();
        }
        Map<List<String>, List<Document>> groups = documents.stream().filter(query::matches).collect(
                groupingBy(d -> groupBy.stream().map(
                        g -> d.getEntryAtPath(g).map(Document.Entry::getValue).orElse(null)).collect(toList())));
        return groups.entrySet().stream().map(e -> new DocumentStats(
                fields.stream().collect(toMap(identity(), f -> getFieldStats(f, e.getValue()), (a, b) -> b)),
                asMap(groupBy, e.getKey()))).collect(toList());
    }

    @Override
    public SearchHistogram getHistogram(GetSearchHistogram request) {
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

        this.search(SearchDocuments.builder().query(query).build())
                .collect(groupingBy(d -> (d.getTimestamp().toEpochMilli() - min) / step))
                .forEach((bucket, hits) -> results.set(bucket.intValue(), (long) hits.size()));
        return new SearchHistogram(query.getSince(), query.getBefore(), results);
    }

    private DocumentStats.FieldStats getFieldStats(String path, List<Document> documents) {
        DocumentStats.FieldStats.FieldStatsBuilder builder = DocumentStats.FieldStats.builder().count(documents.size());
        List<BigDecimal> values =
                documents.stream().flatMap(d -> d.getEntryAtPath(path).map(Stream::of).orElseGet(Stream::empty))
                        .filter(e -> e.getType() == NUMERIC).map(e -> new BigDecimal(e.getValue())).sorted()
                        .collect(toList());
        if (!values.isEmpty()) {
            builder.min(values.get(0));
            builder.max(values.get(values.size() - 1));
            builder.average(DocumentStats.FieldStats.getAverage(values));
        }
        return builder.build();
    }

    private Map<String, String> asMap(List<String> groupBy, List<String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < groupBy.size(); i++) {
            result.put(groupBy.get(i), values.get(i));
        }
        return result;
    }

    @Override
    public void close() {
    }
}
