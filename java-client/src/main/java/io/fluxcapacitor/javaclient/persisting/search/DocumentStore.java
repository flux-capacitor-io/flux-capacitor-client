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

    default void index(Object object, Object collection) {
        index(object instanceof Collection<?> ? (Collection<?>) object : singletonList(object), collection);
    }

    default void index(Object object, Object id, Object collection) {
        index(object, id, collection, null);
    }

    @SneakyThrows
    default void index(Object object, Object id, Object collection, Instant timestamp) {
        index(object, id, collection, timestamp, timestamp, Guarantee.STORED, false).get();
    }

    @SneakyThrows
    default void index(Object object, Object id, Object collection, Instant begin, Instant end) {
        index(object, id, collection, begin, end, Guarantee.STORED, false).get();
    }

    CompletableFuture<Void> index(Object object, Object id, Object collection, Instant begin, Instant end,
                                  Guarantee guarantee, boolean ifNotExists);

    default void index(Collection<?> objects, Object collection) {
        index(objects, collection, v -> getAnnotatedPropertyValue(v, EntityId.class).map(Object::toString)
                .orElseGet(() -> currentIdentityProvider().nextTechnicalId()));
    }

    default <T> void index(Collection<? extends T> objects, Object collection, Function<? super T, ?> idFunction) {
        index(objects, collection, idFunction, v -> null);
    }

    @SneakyThrows
    default void index(Collection<?> objects, Object collection, String idPath, String timestampPath) {
        index(objects, collection, idPath, timestampPath, timestampPath, Guarantee.STORED, false).get();
    }

    @SneakyThrows
    default void index(Collection<?> objects, Object collection, String idPath,
                       String beginPath, String endPath) {
        index(objects, collection, idPath, beginPath, endPath, Guarantee.STORED, false).get();
    }

    CompletableFuture<Void> index(Collection<?> objects, Object collection, String idPath,
                                  String beginPath, String endPath, Guarantee guarantee,
                                  boolean ifNotExists);

    @SneakyThrows
    default <T> void index(Collection<? extends T> objects, Object collection, Function<? super T, ?> idFunction,
                           Function<? super T, Instant> timestampFunction) {
        index(objects, collection, idFunction, timestampFunction, timestampFunction, Guarantee.STORED, false).get();
    }

    @SneakyThrows
    default <T> void index(Collection<? extends T> objects, Object collection, Function<? super T, ?> idFunction,
                           Function<? super T, Instant> beginFunction, Function<? super T, Instant> endFunction) {
        index(objects, collection, idFunction, beginFunction, endFunction, Guarantee.STORED, false).get();
    }

    <T> CompletableFuture<Void> index(Collection<? extends T> objects, Object collection,
                                      Function<? super T, ?> idFunction,
                                      Function<? super T, Instant> beginFunction,
                                      Function<? super T, Instant> endFunction, Guarantee guarantee,
                                      boolean ifNotExists);

    default void indexIfNotExists(Object object, Object collection) {
        indexIfNotExists(object instanceof Collection<?> ? (Collection<?>) object : singletonList(object), collection);
    }

    default void indexIfNotExists(Object object, Object id, Object collection) {
        indexIfNotExists(object, id, collection, null);
    }

    @SneakyThrows
    default void indexIfNotExists(Object object, Object id, Object collection, Instant timestamp) {
        index(object, id, collection, timestamp, timestamp, Guarantee.STORED, true).get();
    }

    @SneakyThrows
    default void indexIfNotExists(Object object, Object id, Object collection, Instant begin, Instant end) {
        index(object, id, collection, begin, end, Guarantee.STORED, true).get();
    }

    default <T> void indexIfNotExists(Collection<? extends T> objects, Object collection) {
        indexIfNotExists(objects, collection, v -> getAnnotatedPropertyValue(v, EntityId.class).map(Object::toString)
                .orElseGet(() -> currentIdentityProvider().nextTechnicalId()));
    }

    default <T> void indexIfNotExists(Collection<? extends T> objects, Object collection,
                                      Function<? super T, ?> idFunction) {
        indexIfNotExists(objects, collection, idFunction, v -> null);
    }

    @SneakyThrows
    default <T> void indexIfNotExists(Collection<? extends T> objects, Object collection, String idPath,
                                      String timestampPath) {
        index(objects, collection, idPath, timestampPath, timestampPath, Guarantee.STORED, true).get();
    }

    @SneakyThrows
    default <T> void indexIfNotExists(Collection<? extends T> objects, Object collection, String idPath,
                                      String beginPath, String endPath) {
        index(objects, collection, idPath, beginPath, endPath, Guarantee.STORED, true).get();
    }

    @SneakyThrows
    default <T> void indexIfNotExists(Collection<? extends T> objects, Object collection,
                                      Function<? super T, ?> idFunction,
                                      Function<? super T, Instant> timestampFunction) {
        index(objects, collection, idFunction, timestampFunction, timestampFunction, Guarantee.STORED, true).get();
    }

    @SneakyThrows
    default <T> void indexIfNotExists(Collection<? extends T> objects, Object collection,
                                      Function<? super T, ?> idFunction,
                                      Function<? super T, Instant> beginFunction,
                                      Function<? super T, Instant> endFunction) {
        index(objects, collection, idFunction, beginFunction, endFunction, Guarantee.STORED, true).get();
    }

    CompletableFuture<Void> bulkUpdate(Collection<BulkUpdate> updates, Guarantee guarantee);

    @SneakyThrows
    default void bulkUpdate(Collection<BulkUpdate> updates) {
        bulkUpdate(updates, Guarantee.STORED).get();
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
