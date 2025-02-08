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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.search.CreateAuditTrail;
import io.fluxcapacitor.common.api.search.DocumentStats;
import io.fluxcapacitor.common.api.search.DocumentUpdate;
import io.fluxcapacitor.common.api.search.FacetEntry;
import io.fluxcapacitor.common.api.search.FacetStats;
import io.fluxcapacitor.common.api.search.GetDocument;
import io.fluxcapacitor.common.api.search.GetSearchHistogram;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import io.fluxcapacitor.common.api.search.SearchHistogram;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@AllArgsConstructor
public class InMemorySearchStore implements SearchClient {
    protected static final Function<SerializedDocument, String> identifier = d -> asIdentifier(d.getCollection(), d.getId());

    protected static String asIdentifier(String collection, String documentId) {
        return collection + "/" + documentId;
    }

    private final Map<String, SerializedDocument> documents = new ConcurrentHashMap<>();

    private final AtomicLong nextIndex = new AtomicLong();
    private final Map<String, ConcurrentSkipListMap<Long, SerializedMessage>> messageLogs = new ConcurrentHashMap<>();
    private final List<BiConsumer<String, List<SerializedMessage>>> monitors = new CopyOnWriteArrayList<>();

    @Getter
    @Setter
    private Duration retentionTime;

    @Override
    public CompletableFuture<Void> index(List<SerializedDocument> documents, Guarantee guarantee, boolean ifNotExists) {
        Map<String, SerializedDocument> updates = documents.stream()
                .collect(toMap(identifier, identity(), (a, b) -> b, LinkedHashMap::new));
        if (ifNotExists) {
            updates.keySet().removeAll(this.documents.keySet());
        }
        this.documents.putAll(updates);
        storeMessages(updates);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Stream<SearchHit<SerializedDocument>> search(SearchDocuments searchDocuments, int fetchSize) {
        SearchQuery query = searchDocuments.getQuery();
        Stream<Document> documentStream = documents.values().stream().map(SerializedDocument::deserializeDocument)
                .filter(query::matches);
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
        return Optional.ofNullable(documents.get(asIdentifier(r.getCollection(), r.getId())));
    }

    @Override
    public CompletableFuture<Void> delete(SearchQuery query, Guarantee guarantee) {
        documents.values().removeIf(query::matches);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> delete(String documentId, String collection, Guarantee guarantee) {
        documents.remove(asIdentifier(collection, documentId));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> createAuditTrail(CreateAuditTrail request) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteCollection(String collection, Guarantee guarantee) {
        documents.values().removeIf(d -> Objects.equals(collection, d.getCollection()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public List<DocumentStats> fetchStatistics(SearchQuery query, List<String> fields, List<String> groupBy) {
        return DocumentStats.compute(documents.values().stream().map(SerializedDocument::deserializeDocument)
                                             .filter(query::matches), fields, groupBy);
    }

    @Override
    public SearchHistogram fetchHistogram(GetSearchHistogram request) {
        SearchQuery query = request.getQuery();
        List<Long> results = IntStream.range(0, request.getResolution()).mapToLong(i -> 0L).boxed().collect(toList());
        if (query.getSince() == null) {
            return new SearchHistogram(null, query.getBefore(), results);
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
    public List<FacetStats> fetchFacetStats(SearchQuery query) {
        return documents.values().stream().filter(query::matches).flatMap(d -> d.getFacets().stream())
                .collect(groupingBy(identity(), TreeMap::new, toList())).values().stream().map(group -> {
                    FacetEntry first = group.getFirst();
                    return new FacetStats(first.getName(), first.getValue(), group.size());
                }).sorted(Comparator.comparing(FacetStats::getCount).reversed()).toList();
    }

    @Override
    public CompletableFuture<Void> bulkUpdate(Collection<DocumentUpdate> updates, Guarantee guarantee) {
        updates.stream().collect(groupingBy(DocumentUpdate::getType)).forEach((type, list) -> {
            switch (type) {
                case delete -> list.forEach(u -> delete(u.getId(), u.getCollection(), guarantee));
                case index -> index(list.stream().map(DocumentUpdate::getObject).toList(), guarantee, false);
                case indexIfNotExists -> index(list.stream().map(DocumentUpdate::getObject).toList(), guarantee, true);
            }
        });
        return CompletableFuture.completedFuture(null);
    }

    public Stream<SerializedMessage> openStream(String collection, Long lastIndex, int maxSize) {
        var map = messageLogs.get(collection);
        if (map == null) {
            return Stream.empty();
        }
        lastIndex = lastIndex == null ? -1L : lastIndex;
        return map.tailMap(lastIndex, false).values().stream().limit(maxSize);
    }

    protected synchronized void storeMessages(Map<String, SerializedDocument> updates) {
        if (!monitors.isEmpty()) {
            Map<String, List<SerializedMessage>> byCollection
                    = updates.values().stream().collect(groupingBy(SerializedDocument::getCollection, mapping(
                    this::asSerializedMessage, toList())));
            try {
                byCollection.forEach((collection, messages) -> {
                    var log = messageLogs.computeIfAbsent(collection, c -> new ConcurrentSkipListMap<>());
                    messages.forEach(m -> log.put(m.getIndex(), m));
                });
                if (retentionTime != null) {
                    purgeExpiredMessages(retentionTime);
                }
            } finally {
                byCollection.forEach(this::notifyMonitors);
            }
        }
    }

    protected SerializedMessage asSerializedMessage(SerializedDocument document) {
        long index = nextIndex.updateAndGet(IndexUtils::nextIndex);
        Metadata metadata = Metadata.of("$start", document.getTimestamp(), "$end", document.getEnd());
        var result = new SerializedMessage(document.getDocument(), metadata, document.getId(),
                                           IndexUtils.millisFromIndex(index));
        result.setIndex(index);
        return result;
    }

    protected void purgeExpiredMessages(Duration messageExpiration) {
        var threshold = FluxCapacitor.currentTime().minus(messageExpiration).toEpochMilli();
        messageLogs.values().forEach(messageLog -> messageLog.headMap(
                IndexUtils.maxIndexFromMillis(threshold), true).clear());
    }

    protected void notifyMonitors(String collection, List<SerializedMessage> messages) {
        this.notifyAll();
        monitors.forEach(m -> m.accept(collection, messages));
    }

    public synchronized Registration registerMonitor(BiConsumer<String, List<SerializedMessage>> monitor) {
        monitors.add(monitor);
        return () -> monitors.remove(monitor);
    }

    public Registration registerMonitor(String collection, Consumer<List<SerializedMessage>> monitor) {
        return registerMonitor((c, messages) -> {
            if (Objects.equals(collection, c)) {
                monitor.accept(messages);
            }
        });
    }

    @Override
    public void close() {
    }
}
