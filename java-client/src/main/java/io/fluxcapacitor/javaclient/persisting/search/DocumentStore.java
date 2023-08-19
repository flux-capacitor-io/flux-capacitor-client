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
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.modeling.SearchParameters;
import io.fluxcapacitor.javaclient.modeling.Searchable;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotationAs;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;
import static java.util.Collections.singletonList;

public interface DocumentStore {

    default CompletableFuture<Void> index(Object object, Object collection) {
        return index(object instanceof Collection<?> ? (Collection<?>) object : singletonList(object), collection);
    }

    default CompletableFuture<Void> index(Object object, Object id, Object collection) {
        return index(object, id, collection, null);
    }

    @SneakyThrows
    default CompletableFuture<Void> index(Object object, Object id, Object collection, Instant timestamp) {
        return index(object, id, collection, timestamp, timestamp, Guarantee.STORED, false);
    }

    @SneakyThrows
    default CompletableFuture<Void> index(Object object, Object id, Object collection, Instant begin, Instant end) {
        return index(object, id, collection, begin, end, Guarantee.STORED, false);
    }

    CompletableFuture<Void> index(Object object, Object id, Object collection, Instant begin, Instant end,
                                  Guarantee guarantee, boolean ifNotExists);

    default CompletableFuture<Void> index(Collection<?> objects, Object collection) {
        return index(objects, collection, v -> getAnnotatedPropertyValue(v, EntityId.class).map(Object::toString)
                .orElseGet(() -> currentIdentityProvider().nextTechnicalId()));
    }

    default <T> CompletableFuture<Void> index(Collection<? extends T> objects, Object collection, Function<? super T, ?> idFunction) {
        return index(objects, collection, idFunction, v -> null);
    }

    @SneakyThrows
    default CompletableFuture<Void> index(Collection<?> objects, Object collection, String idPath, String timestampPath) {
        return index(objects, collection, idPath, timestampPath, timestampPath, Guarantee.STORED, false);
    }

    @SneakyThrows
    default CompletableFuture<Void> index(Collection<?> objects, Object collection, String idPath,
                       String beginPath, String endPath) {
        return index(objects, collection, idPath, beginPath, endPath, Guarantee.STORED, false);
    }

    CompletableFuture<Void> index(Collection<?> objects, Object collection, String idPath,
                                  String beginPath, String endPath, Guarantee guarantee,
                                  boolean ifNotExists);

    @SneakyThrows
    default <T> CompletableFuture<Void> index(Collection<? extends T> objects, Object collection, Function<? super T, ?> idFunction,
                           Function<? super T, Instant> timestampFunction) {
        return index(objects, collection, idFunction, timestampFunction, timestampFunction, Guarantee.STORED, false);
    }

    @SneakyThrows
    default <T> CompletableFuture<Void> index(Collection<? extends T> objects, Object collection, Function<? super T, ?> idFunction,
                           Function<? super T, Instant> beginFunction, Function<? super T, Instant> endFunction) {
        return index(objects, collection, idFunction, beginFunction, endFunction, Guarantee.STORED, false);
    }

    <T> CompletableFuture<Void> index(Collection<? extends T> objects, Object collection,
                                      Function<? super T, ?> idFunction,
                                      Function<? super T, Instant> beginFunction,
                                      Function<? super T, Instant> endFunction, Guarantee guarantee,
                                      boolean ifNotExists);

    default CompletableFuture<Void> indexIfNotExists(Object object, Object collection) {
        return indexIfNotExists(object instanceof Collection<?> ? (Collection<?>) object : singletonList(object), collection);
    }

    default CompletableFuture<Void> indexIfNotExists(Object object, Object id, Object collection) {
        return indexIfNotExists(object, id, collection, null);
    }

    @SneakyThrows
    default CompletableFuture<Void> indexIfNotExists(Object object, Object id, Object collection, Instant timestamp) {
        return index(object, id, collection, timestamp, timestamp, Guarantee.STORED, true);
    }

    @SneakyThrows
    default CompletableFuture<Void> indexIfNotExists(Object object, Object id, Object collection, Instant begin, Instant end) {
        return index(object, id, collection, begin, end, Guarantee.STORED, true);
    }

    default <T> CompletableFuture<Void> indexIfNotExists(Collection<? extends T> objects, Object collection) {
        return indexIfNotExists(objects, collection, v -> getAnnotatedPropertyValue(v, EntityId.class).map(Object::toString)
                .orElseGet(() -> currentIdentityProvider().nextTechnicalId()));
    }

    default <T> CompletableFuture<Void> indexIfNotExists(Collection<? extends T> objects, Object collection,
                                      Function<? super T, ?> idFunction) {
        return indexIfNotExists(objects, collection, idFunction, v -> null);
    }

    @SneakyThrows
    default <T> CompletableFuture<Void> indexIfNotExists(Collection<? extends T> objects, Object collection, String idPath,
                                      String timestampPath) {
        return index(objects, collection, idPath, timestampPath, timestampPath, Guarantee.STORED, true);
    }

    @SneakyThrows
    default <T> CompletableFuture<Void> indexIfNotExists(Collection<? extends T> objects, Object collection, String idPath,
                                      String beginPath, String endPath) {
        return index(objects, collection, idPath, beginPath, endPath, Guarantee.STORED, true);
    }

    @SneakyThrows
    default <T> CompletableFuture<Void> indexIfNotExists(Collection<? extends T> objects, Object collection,
                                      Function<? super T, ?> idFunction,
                                      Function<? super T, Instant> timestampFunction) {
        return index(objects, collection, idFunction, timestampFunction, timestampFunction, Guarantee.STORED, true);
    }

    @SneakyThrows
    default <T> CompletableFuture<Void> indexIfNotExists(Collection<? extends T> objects, Object collection,
                                      Function<? super T, ?> idFunction,
                                      Function<? super T, Instant> beginFunction,
                                      Function<? super T, Instant> endFunction) {
        return index(objects, collection, idFunction, beginFunction, endFunction, Guarantee.STORED, true);
    }

    CompletableFuture<Void> bulkUpdate(Collection<BulkUpdate> updates, Guarantee guarantee);

    @SneakyThrows
    default CompletableFuture<Void> bulkUpdate(Collection<BulkUpdate> updates) {
        return bulkUpdate(updates, Guarantee.STORED);
    }

    default Search search(@NonNull Object collection, Object... additionalCollections) {
        return search(SearchQuery.builder().collections(
                Stream.concat(Stream.of(collection), Arrays.stream(additionalCollections))
                        .map(c -> c instanceof Class<?> type ?
                                getAnnotationAs(type, Searchable.class, SearchParameters.class)
                                        .map(SearchParameters::getCollection)
                                        .orElseThrow(() -> new IllegalArgumentException(
                                                "Class is missing @Searchable: " + type)) : c.toString()).toList()));
    }

    Search search(SearchQuery.Builder queryBuilder);

    <T> Optional<T> fetchDocument(Object id, Object collection);

    <T> Optional<T> fetchDocument(Object id, Object collection, Class<T> type);

    CompletableFuture<Void> deleteDocument(Object id, Object collection);

    CompletableFuture<Void> deleteCollection(Object collection);

    CompletableFuture<Void> createAuditTrail(Object collection, Duration retentionTime);

    DocumentSerializer getSerializer();

}
