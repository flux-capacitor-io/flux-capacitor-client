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
import io.fluxcapacitor.common.api.search.BulkUpdate;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.CreateAuditTrail;
import io.fluxcapacitor.common.api.search.DocumentStats;
import io.fluxcapacitor.common.api.search.DocumentUpdate;
import io.fluxcapacitor.common.api.search.FacetStats;
import io.fluxcapacitor.common.api.search.GetDocument;
import io.fluxcapacitor.common.api.search.GetSearchHistogram;
import io.fluxcapacitor.common.api.search.Group;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import io.fluxcapacitor.common.api.search.SearchHistogram;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocumentIfNotExists;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;
import static java.lang.String.format;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@AllArgsConstructor
@Slf4j
public class DefaultDocumentStore implements DocumentStore, HasLocalHandlers {
    private final SearchClient client;
    @Getter
    private final DocumentSerializer serializer;
    @Delegate
    private final HasLocalHandlers handlerRegistry;

    @Override
    public CompletableFuture<Void> index(@NonNull Object object, Object id, Object collection, Instant begin,
                                         Instant end, Guarantee guarantee, boolean ifNotExists) {
        try {
            return client.index(
                    List.of(serializer.toDocument(object, id.toString(), determineCollection(collection), begin, end)),
                    guarantee, ifNotExists);
        } catch (Exception e) {
            throw new DocumentStoreException(format(
                    "Failed to store a document %s to collection %s", id, collection), e);
        }
    }

    @Override
    public CompletableFuture<Void> index(Collection<?> objects, Object collection,
                                         String idPath, String beginPath,
                                         String endPath, Guarantee guarantee, boolean ifNotExists) {
        var documents = objects.stream().map(v -> serializer.toDocument(
                        v, currentIdentityProvider().nextTechnicalId(), determineCollection(collection), null, null))
                .map(SerializedDocument::deserializeDocument).map(d -> {
                    Document.DocumentBuilder builder = d.toBuilder();
                    if (StringUtils.hasText(idPath)) {
                        builder.id(d.getEntryAtPath(idPath).filter(
                                        e -> e.getType() == Document.EntryType.TEXT
                                             || e.getType() == Document.EntryType.NUMERIC)
                                           .map(Document.Entry::getValue).orElseThrow(
                                        () -> new IllegalArgumentException(
                                                "Could not determine the document id. Path does not exist on document: "
                                                + d)));
                    }
                    if (StringUtils.hasText(beginPath)) {
                        builder.timestamp(
                                d.getEntryAtPath(beginPath).filter(e -> e.getType() == Document.EntryType.TEXT)
                                        .map(Document.Entry::getValue).map(Instant::parse)
                                        .orElse(null));
                    }
                    if (StringUtils.hasText(endPath)) {
                        builder.end(d.getEntryAtPath(endPath).filter(e -> e.getType() == Document.EntryType.TEXT)
                                            .map(Document.Entry::getValue).map(Instant::parse)
                                            .orElse(null));
                    }
                    return builder.build();
                }).map(SerializedDocument::new).collect(toList());
        try {
            return client.index(documents, guarantee, ifNotExists);
        } catch (Exception e) {
            throw new DocumentStoreException(
                    format("Could not store a list of documents for collection %s", collection), e);
        }
    }

    @Override
    public <T> CompletableFuture<Void> index(Collection<? extends T> objects, Object collection,
                                             Function<? super T, ?> idFunction,
                                             Function<? super T, Instant> beginFunction,
                                             Function<? super T, Instant> endFunction, Guarantee guarantee,
                                             boolean ifNotExists) {
        var documents = objects.stream().map(v -> serializer.toDocument(
                v, idFunction.apply(v).toString(), determineCollection(collection), beginFunction.apply(v),
                endFunction.apply(v))).collect(toList());
        try {
            return client.index(documents, guarantee, ifNotExists);
        } catch (Exception e) {
            throw new DocumentStoreException(
                    format("Could not store a list of documents for collection %s", collection), e);
        }
    }

    @Override
    public CompletableFuture<Void> bulkUpdate(Collection<? extends BulkUpdate> updates, Guarantee guarantee) {
        try {
            return client.bulkUpdate(updates.stream().map(this::serializeAction)
                                             .collect(toMap(a -> format("%s_%s", a.getCollection(), a.getId()),
                                                            identity(), (a, b) -> b)).values(),
                                     guarantee);
        } catch (Exception e) {
            throw new DocumentStoreException("Could not apply batch of search actions", e);
        }
    }

    public DocumentUpdate serializeAction(BulkUpdate update) {
        String collection = determineCollection(update.getCollection());
        DocumentUpdate.Builder builder = DocumentUpdate.builder()
                .collection(collection).id(update.getId()).type(update.getType());
        if (update instanceof IndexDocument u) {
            return builder.object(serializer.toDocument(
                    u.getObject(), u.getId(), collection, u.getTimestamp(), u.getEnd())).build();
        } else if (update instanceof IndexDocumentIfNotExists u) {
            return builder.object(serializer.toDocument(
                    u.getObject(), u.getId(), collection, u.getTimestamp(), u.getEnd())).build();
        }
        return builder.build();
    }


    @Override
    public Search search(SearchQuery.Builder searchBuilder) {
        return new DefaultSearch(searchBuilder);
    }

    @Override
    public <T> Optional<T> fetchDocument(Object id, Object collection) {
        try {
            return client.fetch(new GetDocument(id.toString(), determineCollection(collection)))
                    .map(serializer::fromDocument);
        } catch (Exception e) {
            throw new DocumentStoreException(format("Could not get document %s from collection %s", id, collection), e);
        }
    }

    @Override
    public <T> Optional<T> fetchDocument(Object id, Object collection, Class<T> type) {
        try {
            return client.fetch(new GetDocument(id.toString(), determineCollection(collection)))
                    .map(d -> serializer.fromDocument(d, type));
        } catch (Exception e) {
            throw new DocumentStoreException(format("Could not get document %s from collection %s", id, collection), e);
        }
    }

    @Override
    public CompletableFuture<Void> deleteDocument(Object id, Object collection) {
        try {
            return client.delete(id.toString(), determineCollection(collection), Guarantee.STORED);
        } catch (Exception e) {
            throw new DocumentStoreException(format("Could not delete document %s from collection %s", id, collection),
                                             e);
        }
    }

    @Override
    public CompletableFuture<Void> deleteCollection(Object collection) {
        try {
            return client.deleteCollection(determineCollection(collection));
        } catch (Exception e) {
            throw new DocumentStoreException(format("Could not delete collection %s", collection), e);
        }
    }

    @Override
    public CompletableFuture<Void> createAuditTrail(Object collection, Duration retentionTime) {
        try {
            return client.createAuditTrail(new CreateAuditTrail(determineCollection(collection), Optional.ofNullable(
                    retentionTime).map(Duration::getSeconds).orElse(null), Guarantee.STORED));
        } catch (Exception e) {
            throw new DocumentStoreException(format("Could not create audit trail %s", collection), e);
        }
    }

    @RequiredArgsConstructor
    protected class DefaultSearch implements Search {

        public static int defaultFetchSize = 10_000;

        private final SearchQuery.Builder queryBuilder;
        private final List<String> sorting = new ArrayList<>();
        private final List<String> pathFilters = new ArrayList<>();
        private volatile int skip;

        protected DefaultSearch() {
            this(SearchQuery.builder());
        }

        @Override
        public Search since(Instant start, boolean inclusive) {
            queryBuilder.since(start).sinceExclusive(!inclusive);
            return this;
        }

        @Override
        public Search before(Instant end, boolean inclusive) {
            queryBuilder.before(end).beforeInclusive(inclusive);
            return this;
        }

        @Override
        public Search inPeriod(Instant start, boolean startInclusive, Instant end, boolean endInclusive) {
            queryBuilder.since(start).sinceExclusive(!startInclusive).before(end).beforeInclusive(endInclusive);
            return this;
        }

        @Override
        public Search constraint(Constraint... constraints) {
            switch (constraints.length) {
                case 0:
                    break;
                case 1:
                    queryBuilder.constraint(constraints[0]);
                    break;
                default:
                    queryBuilder.constraints(Arrays.asList(constraints));
                    break;
            }
            return this;
        }

        @Override
        public Search sortByTimestamp(boolean descending) {
            return sortBy("timestamp", descending);
        }

        @Override
        public Search sortByScore() {
            sorting.add("-score");
            return this;
        }

        @Override
        public Search sortBy(String path, boolean descending) {
            sorting.add((descending ? "-" : "") + path);
            return this;
        }

        @Override
        public Search exclude(String... paths) {
            pathFilters.addAll(Arrays.stream(paths).map(p -> "-" + p).toList());
            return this;
        }

        @Override
        public Search includeOnly(String... paths) {
            pathFilters.addAll(Arrays.asList(paths));
            return this;
        }

        @Override
        public Search skip(Integer n) {
            if (n != null) {
                this.skip = n;
            }
            return this;
        }

        @Override
        public <T> Stream<SearchHit<T>> streamHits() {
            return fetchHitStream(null, null);
        }

        @Override
        public <T> Stream<SearchHit<T>> streamHits(int fetchSize) {
            return fetchHitStream(null, null, fetchSize);
        }

        @Override
        public <T> Stream<SearchHit<T>> streamHits(Class<T> type) {
            return fetchHitStream(null, type);
        }

        @Override
        public <T> Stream<SearchHit<T>> streamHits(Class<T> type, int fetchSize) {
            return fetchHitStream(null, type, fetchSize);
        }

        @Override
        public <T> List<T> fetch(int maxSize) {
            return this.<T>fetchHitStream(maxSize, null).map(SearchHit::getValue).collect(toList());
        }

        @Override
        public <T> List<T> fetch(int maxSize, Class<T> type) {
            return fetchHitStream(maxSize, type).map(SearchHit::getValue).collect(toList());
        }

        protected <T> Stream<SearchHit<T>> fetchHitStream(Integer maxSize, Class<T> type) {
            return fetchHitStream(maxSize, type, maxSize == null
                    ? defaultFetchSize : Math.min(maxSize, defaultFetchSize));
        }

        protected <T> Stream<SearchHit<T>> fetchHitStream(Integer maxSize, Class<T> type, int fetchSize) {
            Function<SerializedDocument, T> convertFunction = type == null
                    ? serializer::fromDocument : document -> serializer.fromDocument(document, type);
            return client.search(SearchDocuments.builder().query(queryBuilder.build()).maxSize(maxSize).sorting(sorting)
                                         .pathFilters(pathFilters).skip(skip).build(), fetchSize)
                    .map(hit -> hit.map(convertFunction));
        }

        @Override
        public SearchHistogram fetchHistogram(int resolution, int maxSize) {
            return client.fetchHistogram(new GetSearchHistogram(queryBuilder.build(), resolution, maxSize));
        }

        @Override
        public GroupSearch groupBy(String... paths) {
            return new DefaultGroupSearch(Arrays.asList(paths));
        }

        @Override
        public List<FacetStats> facetStats() {
            return client.fetchFacetStats(queryBuilder.build());
        }

        @Override
        public CompletableFuture<Void> delete() {
            return client.delete(queryBuilder.build(), Guarantee.STORED);
        }

        @AllArgsConstructor
        protected class DefaultGroupSearch implements GroupSearch {
            private final List<String> groupBy;

            @Override
            public Map<Group, Map<String, DocumentStats.FieldStats>> aggregate(String... fields) {
                return client.fetchStatistics(queryBuilder.build(), Arrays.asList(fields), groupBy).stream()
                        .collect(toMap(DocumentStats::getGroup, DocumentStats::getFieldStats));
            }
        }
    }
}
