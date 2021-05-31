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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.search.*;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocumentIfNotExists;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.common.IdentityProvider;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@AllArgsConstructor
@Slf4j
public class DefaultDocumentStore implements DocumentStore {
    private final SearchClient client;
    @Getter
    private final DocumentSerializer serializer;

    @Override
    public CompletableFuture<Void> index(Object object, String id, String collection, Instant timestamp,
                                         Instant end, Guarantee guarantee, boolean ifNotExists) {
        try {
            return client.index(singletonList(serializer.toDocument(object, id, collection, timestamp, end)),
                    guarantee, ifNotExists).asCompletableFuture();
        } catch (Exception e) {
            throw new DocumentStoreException(format("Could not store a document %s for id %s", object, id), e);
        }
    }

    @Override
    public <T> CompletableFuture<Void> index(Collection<? extends T> objects, String collection,
                                             @Nullable String idPath, @Nullable String timestampPath,
                                             @Nullable String endPath, Guarantee guarantee, boolean ifNotExists) {
        IdentityProvider identityProvider = currentIdentityProvider();
        List<Document> documents = objects.stream().map(v -> serializer.toDocument(
                v, identityProvider.nextId(), collection, null, null)).map(d -> {
            Document.DocumentBuilder builder = d.toBuilder();
            if (idPath != null) {
                builder.id(d.getEntryAtPath(idPath).map(Document.Entry::getValue).orElseThrow(
                        () -> new IllegalArgumentException(
                                "Could not determine the document id. Path does not exist on document: " + d)));
            }
            if (timestampPath != null) {
                builder.timestamp(d.getEntryAtPath(timestampPath).map(Document.Entry::getValue).map(Instant::parse)
                        .orElse(null));
            }
            if (endPath != null) {
                builder.end(d.getEntryAtPath(endPath).map(Document.Entry::getValue).map(Instant::parse)
                        .orElse(null));
            }
            return builder.build();
        }).collect(toList());
        try {
            return client.index(documents, guarantee, ifNotExists).asCompletableFuture();
        } catch (Exception e) {
            throw new DocumentStoreException(
                    format("Could not store a list of documents for collection %s", collection), e);
        }
    }

    @Override
    public <T> CompletableFuture<Void> index(Collection<? extends T> objects, String collection,
                                             Function<? super T, String> idFunction,
                                             Function<? super T, Instant> timestampFunction,
                                             Function<? super T, Instant> endFunction, Guarantee guarantee,
                                             boolean ifNotExists) {
        List<Document> documents = objects.stream().map(v -> serializer.toDocument(
                v, idFunction.apply(v), collection, timestampFunction.apply(v),
                endFunction.apply(v))).collect(toList());
        try {
            return client.index(documents, guarantee, ifNotExists).asCompletableFuture();
        } catch (Exception e) {
            throw new DocumentStoreException(
                    format("Could not store a list of documents for collection %s", collection), e);
        }
    }

    @Override
    public CompletableFuture<Void> bulkUpdate(Collection<BulkUpdate> updates, Guarantee guarantee) {
        try {
            return client.bulkUpdate(updates.stream().map(this::serializeAction).filter(Objects::nonNull)
                            .collect(toMap(a -> format("%s_%s", a.getCollection(), a.getId()), identity(), (a, b) -> b)).values(),
                    guarantee).asCompletableFuture();
        } catch (Exception e) {
            throw new DocumentStoreException("Could not apply batch of search actions", e);
        }
    }

    public SerializedDocumentUpdate serializeAction(BulkUpdate update) {
        SerializedDocumentUpdate.Builder builder = SerializedDocumentUpdate.builder()
                .collection(update.getCollection()).id(update.getId()).type(update.getType());
        if (update instanceof IndexDocument) {
            return builder.object(new SerializedDocument(serializer.toDocument((IndexDocument) update))).build();
        } else if (update instanceof IndexDocumentIfNotExists) {
            return builder.object(new SerializedDocument(serializer.toDocument((IndexDocumentIfNotExists) update))).build();
        }
        return builder.build();
    }


    @Override
    public Search search(SearchQuery.Builder searchBuilder) {
        return new DefaultSearch(searchBuilder);
    }

    @Override
    public void deleteDocument(String id, String collection) {
        try {
            client.delete(id, collection, Guarantee.SENT).await();
        } catch (Exception e) {
            throw new DocumentStoreException(format("Could not delete document %s", collection), e);
        }
    }

    @Override
    public void deleteCollection(String collection) {
        try {
            client.deleteCollection(collection).await();
        } catch (Exception e) {
            throw new DocumentStoreException(format("Could not delete collection %s", collection), e);
        }
    }

    @Override
    public void createAuditTrail(String collection, Duration retentionTime) {
        try {
            client.createAuditTrail(new CreateAuditTrail(collection, Optional.ofNullable(
                    retentionTime).map(Duration::getSeconds).orElse(null))).await();
        } catch (Exception e) {
            throw new DocumentStoreException(format("Could not create audit trail %s", collection), e);
        }
    }

    @RequiredArgsConstructor
    private class DefaultSearch implements Search {
        private final SearchQuery.Builder queryBuilder;
        private final List<String> sorting = new ArrayList<>();
        private final List<String> pathFilters = new ArrayList<>();
        private volatile int skip;

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
            sorting.add(descending ? "-" : "" + "timestamp");
            return this;
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
            pathFilters.addAll(Arrays.stream(paths).map(p -> "-" + p).collect(toList()));
            return this;
        }

        @Override
        public Search includeOnly(String... paths) {
            pathFilters.addAll(Arrays.asList(paths));
            return this;
        }

        @Override
        public Search skip(int n) {
            this.skip = n;
            return this;
        }

        @Override
        public <T> Stream<SearchHit<T>> streamHits() {
            return getHitStream(null, null);
        }

        @Override
        public <T> Stream<SearchHit<T>> streamHits(Class<T> type) {
            return getHitStream(null, type);
        }

        @Override
        public <T> List<T> get(int maxSize) {
            return this.<T>getHitStream(maxSize, null).map(SearchHit::getValue).collect(toList());
        }

        @Override
        public <T> List<T> get(int maxSize, Class<T> type) {
            return getHitStream(maxSize, type).map(SearchHit::getValue).collect(toList());
        }

        protected <T> Stream<SearchHit<T>> getHitStream(Integer maxSize, Class<T> type) {
            Function<Document, T> convertFunction = type == null
                    ? serializer::fromDocument : document -> serializer.fromDocument(document, type);
            return client.search(SearchDocuments.builder().query(queryBuilder.build()).maxSize(maxSize).sorting(sorting)
                    .pathFilters(pathFilters).skip(skip).build()).map(hit -> hit.map(convertFunction));
        }

        @Override
        public SearchHistogram getHistogram(int resolution, int maxSize) {
            return client.getHistogram(new GetSearchHistogram(queryBuilder.build(), resolution, maxSize));
        }

        @Override
        public List<DocumentStats> getStatistics(@NonNull Object field, String... groupBy) {
            List<String> fields;
            if (field instanceof String) {
                fields = singletonList((String) field);
            } else if (field instanceof Collection<?>) {
                fields = ((Collection<?>) field).stream().map(Objects::toString).collect(toList());
            } else {
                throw new IllegalArgumentException(
                        "Failed to parse field. Expected Collection or String. Got: " + field);
            }
            return client.getStatistics(queryBuilder.build(), fields, Arrays.<String>asList(groupBy));
        }

        @Override
        public CompletableFuture<Void> delete() {
            return client.delete(queryBuilder.build(), Guarantee.STORED).asCompletableFuture();
        }
    }
}
