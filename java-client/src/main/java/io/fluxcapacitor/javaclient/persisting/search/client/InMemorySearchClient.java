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
import io.fluxcapacitor.common.api.search.DocumentStats;
import io.fluxcapacitor.common.api.search.SearchHistogram;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.search.Document.EntryType.NUMERIC;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class InMemorySearchClient implements SearchClient {
    private final List<Document> documents = new CopyOnWriteArrayList<>();

    @Override
    public Awaitable index(List<Document> documents, Guarantee guarantee) {
        this.documents.addAll(documents);
        return Awaitable.ready();
    }

    @Override
    public Stream<SearchHit<Document>> search(SearchQuery query, List<String> sorting) {
        Stream<Document> documentStream = documents.stream().filter(query::matches);
        return documentStream.sorted(createComparator(sorting.isEmpty() ? singletonList("-timestamp") : sorting))
                .map(d -> new SearchHit<>(d.getId(), d.getCollection(), d.getTimestamp(), () -> d));
    }

    @Override
    public Awaitable delete(SearchQuery query, Guarantee guarantee) {
        documents.removeAll(documents.stream().filter(query::matches).collect(Collectors.toList()));
        return Awaitable.ready();
    }

    @Override
    public Awaitable delete(String collection, String documentId, Guarantee guarantee) {
        documents.removeIf(d -> Objects.equals(documentId, d.getId()) && Objects.equals(collection, d.getCollection()));
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
                fields.stream().collect(toMap(Function.identity(), f -> getFieldStats(f, e.getValue()), (a, b) -> b)),
                asMap(groupBy, e.getKey()))).collect(toList());
    }

    @Override
    public SearchHistogram getHistogram(SearchQuery query, int resolution) {
        List<Long> results = IntStream.range(0, resolution).mapToLong(i -> 0L).boxed().collect(toList());
        if (query.getSince() == null) {
            return new SearchHistogram(query.getSince(), query.getBefore(), results);
        }
        if (query.getBefore() == null) {
            query = query.toBuilder().before(Instant.now()).build();
        }
        long min = query.getSince().toEpochMilli();
        long delta = query.getBefore().toEpochMilli() - min;
        long step = Math.min(1, delta / resolution);

        this.search(query, singletonList("timestamp"))
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

    private Comparator<Document> createComparator(List<String> sorting) {
        return sorting.stream().map(s -> {
            switch (s) {
                case "-timestamp":
                    return Comparator.comparing(Document::getTimestamp).reversed();
                case "timestamp":
                    return Comparator.comparing(Document::getTimestamp);
                default:
                    boolean reversed = s.startsWith("-");
                    String path = reversed ? s.substring(1) : s;
                    Comparator<Document> valueComparator =
                            Comparator.nullsLast(Comparator.comparing(d -> d.getEntryAtPath(path).orElse(null)));
                    return reversed ? valueComparator.reversed() : valueComparator;
            }
        }).reduce(Comparator::thenComparing).orElse((a, b) -> 0);
    }

    @Override
    public void close() {
    }
}
