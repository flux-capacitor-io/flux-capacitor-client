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

package io.fluxcapacitor.javaclient.persisting.search;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.CreateAuditTrail;
import io.fluxcapacitor.common.api.search.DocumentStats;
import io.fluxcapacitor.common.api.search.SearchHistogram;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.common.IdentityProvider;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentClock;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;
import static java.util.Collections.singletonList;

@AllArgsConstructor
@Slf4j
public class DefaultDocumentStore implements DocumentStore {
    private final SearchClient client;
    @Getter
    private final DocumentSerializer serializer;


    @Override
    public CompletableFuture<Void> index(Object object, String id, String collection, Instant timestamp,
                                         Guarantee guarantee) {
        try {
            Awaitable awaitable =
                    client.index(singletonList(serializer.toDocument(object, id, collection, timestamp)), guarantee);
            return CompletableFuture.runAsync(awaitable::awaitSilently);
        } catch (Exception e) {
            throw new DocumentStoreException(String.format("Could not store a document %s for id %s", object, id), e);
        }
    }

    @Override
    public <T> CompletableFuture<Void> index(Collection<? extends T> objects, String collection,
                                             @Nullable String idPath,
                                             @Nullable String timestampPath, Guarantee guarantee) {
        IdentityProvider identityProvider = currentIdentityProvider();
        Clock clock = currentClock();
        List<Document> documents = objects.stream().map(v -> serializer.toDocument(
                v, identityProvider.nextId(), collection, clock.instant())).map(d -> {
            Document.DocumentBuilder builder = d.toBuilder();
            if (idPath != null) {
                builder.id(d.getEntryAtPath(idPath).map(Document.Entry::getValue).orElseThrow(
                        () -> new IllegalArgumentException(
                                "Could not determine the document id. Path does not exist on document: " + d)));
            }
            if (timestampPath != null) {
                builder.timestamp(d.getEntryAtPath(timestampPath).map(Document.Entry::getValue).map(Instant::parse)
                                          .orElse(d.getTimestamp()));
            }
            return builder.build();
        }).collect(Collectors.toList());
        try {
            Awaitable awaitable = client.index(documents, guarantee);
            return CompletableFuture.runAsync(awaitable::awaitSilently);
        } catch (Exception e) {
            throw new DocumentStoreException(
                    String.format("Could not store a list of documents for collection %s", collection), e);
        }
    }

    @Override
    public <T> CompletableFuture<Void> index(Collection<? extends T> objects, String collection,
                                             Function<? super T, String> idFunction,
                                             Function<? super T, Instant> timestampFunction,
                                             Guarantee guarantee) {
        List<Document> documents = objects.stream().map(v -> serializer.toDocument(
                v, idFunction.apply(v), collection, Optional.ofNullable(timestampFunction.apply(v))
                        .orElseGet(() -> currentClock().instant()))).collect(Collectors.toList());
        try {
            Awaitable awaitable = client.index(documents, guarantee);
            return CompletableFuture.runAsync(awaitable::awaitSilently);
        } catch (Exception e) {
            throw new DocumentStoreException(
                    String.format("Could not store a list of documents for collection %s", collection), e);
        }
    }

    @Override
    public Search search(SearchQuery.Builder searchBuilder) {
        return new DefaultSearch(searchBuilder);
    }

    @Override
    public void deleteDocument(String collection, String id) {
        try {
            client.delete(collection, id, Guarantee.SENT).await();
        } catch (Exception e) {
            throw new DocumentStoreException(String.format("Could not delete document %s", collection), e);
        }
    }

    @Override
    public void deleteCollection(String collection) {
        try {
            client.deleteCollection(collection).await();
        } catch (Exception e) {
            throw new DocumentStoreException(String.format("Could not delete collection %s", collection), e);
        }
    }

    @Override
    public void createAuditTrail(String collection, Duration retentionTime) {
        try {
            client.createAuditTrail(new CreateAuditTrail(collection, Optional.ofNullable(
                    retentionTime).map(Duration::getSeconds).orElse(null))).await();
        } catch (Exception e) {
            throw new DocumentStoreException(String.format("Could not create audit trail %s", collection), e);
        }
    }

    @AllArgsConstructor
    private class DefaultSearch implements Search {
        private final SearchQuery.Builder queryBuilder;
        private final List<String> sorting = new ArrayList<>();

        protected DefaultSearch() {
            this(SearchQuery.builder());
        }

        @Override
        public Search inPeriod(Instant start, Instant endExclusive, boolean requireTimestamp) {
            if (start != null) {
                queryBuilder.since(start);
            }
            if (endExclusive != null) {
                queryBuilder.before(endExclusive);
            }
            queryBuilder.requireTimestamp(requireTimestamp);
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
            sorting.add(descending ? "-" : "" + "_timestamp");
            return this;
        }

        @Override
        public Search sortByScore() {
            sorting.add("-_score");
            return this;
        }

        @Override
        public Search sortBy(String path, boolean descending) {
            sorting.add(descending ? "-" : "" + path);
            return this;
        }

        @Override
        public <T> Stream<SearchHit<T>> stream() {
            return client.search(queryBuilder.build(), sorting).map(hit -> hit.map(serializer::fromDocument));
        }

        @Override
        public <T> Stream<SearchHit<T>> stream(Class<T> type) {
            return client.search(queryBuilder.build(), sorting).map(hit -> hit.map(document -> serializer
                    .fromDocument(document, type)));
        }

        @Override
        public SearchHistogram getHistogram(int resolution, Integer maxSize) {
            return client.getHistogram(queryBuilder.build(), resolution, maxSize);
        }

        @Override
        public List<DocumentStats> getStatistics(@NonNull Object field, String... groupBy) {
            List<String> fields;
            if (field instanceof String) {
                fields = singletonList((String) field);
            } else if (field instanceof Collection<?>) {
                fields = ((Collection<?>) field).stream().map(Objects::toString).collect(Collectors.toList());
            } else {
                throw new IllegalArgumentException(
                        "Failed to parse field. Expected Collection or String. Got: " + field);
            }
            return client.getStatistics(queryBuilder.build(), fields, Arrays.<String>asList(groupBy));
        }

        @Override
        public void delete(Guarantee guarantee) {
            client.delete(queryBuilder.build(), guarantee);
        }
    }
}
