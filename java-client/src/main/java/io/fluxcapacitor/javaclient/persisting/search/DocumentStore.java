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
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.modeling.SearchParameters;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getSearchParameters;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

public interface DocumentStore {

    default CompletableFuture<Void> index(@NonNull Object object) {
        if (object.getClass().isArray()) {
            if (!object.getClass().arrayType().isPrimitive()) {
                object = Arrays.asList((Object[]) object);
            }
        }
        if (object instanceof Collection<?> collection) {
            return CompletableFuture.allOf(collection.stream().map(this::index).toArray(CompletableFuture[]::new));
        }
        Class<?> type = object.getClass();
        var searchParams = ofNullable(getSearchParameters(type)).orElse(SearchParameters.defaultSearchParameters);
        String collection = ofNullable(searchParams.getCollection()).orElseGet(type::getSimpleName);
        Instant begin = ReflectionUtils.<Instant>readProperty(searchParams.getTimestampPath(), object).orElse(null);
        Instant end = ReflectionUtils.hasProperty(searchParams.getEndPath(), object)
                ? ReflectionUtils.<Instant>readProperty(searchParams.getEndPath(), object).orElse(null) : begin;
        String id = getAnnotatedPropertyValue(object, EntityId.class).map(Object::toString)
                .orElseGet(() -> currentIdentityProvider().nextTechnicalId());
        return index(object, id, collection, begin, end);
    }

    default CompletableFuture<Void> index(@NonNull Object object, Object collection) {
        if (object.getClass().isArray()) {
            if (!object.getClass().arrayType().isPrimitive()) {
                object = Arrays.asList((Object[]) object);
            }
        }
        if (object instanceof Collection<?> col) {
            return CompletableFuture.allOf(col.stream().map(v -> index(v, collection)).toArray(CompletableFuture[]::new));
        }
        Class<?> type = object.getClass();
        var searchParams = ofNullable(getSearchParameters(type)).orElse(SearchParameters.defaultSearchParameters);
        Instant begin = ReflectionUtils.<Instant>readProperty(searchParams.getTimestampPath(), object).orElse(null);
        Instant end = ReflectionUtils.hasProperty(searchParams.getEndPath(), object)
                ? ReflectionUtils.<Instant>readProperty(searchParams.getEndPath(), object).orElse(null) : begin;
        String id = getAnnotatedPropertyValue(object, EntityId.class).map(Object::toString)
                .orElseGet(() -> currentIdentityProvider().nextTechnicalId());
        return index(object, id, collection, begin, end);
    }

    default CompletableFuture<Void> index(@NonNull Object object, Object id, Object collection) {
        return index(object, id, collection, null);
    }

    @SneakyThrows
    default CompletableFuture<Void> index(@NonNull Object object, Object id, Object collection, Instant timestamp) {
        return index(object, id, collection, timestamp, timestamp, Guarantee.STORED, false);
    }

    @SneakyThrows
    default CompletableFuture<Void> index(@NonNull Object object, Object id, Object collection, Instant begin, Instant end) {
        return index(object, id, collection, begin, end, Guarantee.STORED, false);
    }

    CompletableFuture<Void> index(@NotNull Object object, Object id, Object collection, Instant begin, Instant end,
                                  Guarantee guarantee, boolean ifNotExists);

    default CompletableFuture<Void> index(Collection<?> objects, Object collection) {
        return index(objects, collection, v -> getAnnotatedPropertyValue(v, EntityId.class).map(Object::toString)
                .orElseGet(() -> currentIdentityProvider().nextTechnicalId()));
    }

    default <T> CompletableFuture<Void> index(Collection<? extends T> objects, Object collection,
                                              Function<? super T, ?> idFunction) {
        return index(objects, collection, idFunction, v -> null);
    }

    @SneakyThrows
    default CompletableFuture<Void> index(Collection<?> objects, Object collection, String idPath,
                                          String timestampPath) {
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
    default <T> CompletableFuture<Void> index(Collection<? extends T> objects, Object collection,
                                              Function<? super T, ?> idFunction,
                                              Function<? super T, Instant> timestampFunction) {
        return index(objects, collection, idFunction, timestampFunction, timestampFunction, Guarantee.STORED, false);
    }

    @SneakyThrows
    default <T> CompletableFuture<Void> index(Collection<? extends T> objects, Object collection,
                                              Function<? super T, ?> idFunction,
                                              Function<? super T, Instant> beginFunction,
                                              Function<? super T, Instant> endFunction) {
        return index(objects, collection, idFunction, beginFunction, endFunction, Guarantee.STORED, false);
    }

    <T> CompletableFuture<Void> index(Collection<? extends T> objects, Object collection,
                                      Function<? super T, ?> idFunction,
                                      Function<? super T, Instant> beginFunction,
                                      Function<? super T, Instant> endFunction, Guarantee guarantee,
                                      boolean ifNotExists);

    default CompletableFuture<Void> indexIfNotExists(Object object, Object collection) {
        return indexIfNotExists(object instanceof Collection<?> ? (Collection<?>) object : singletonList(object),
                                collection);
    }

    default CompletableFuture<Void> indexIfNotExists(Object object, Object id, Object collection) {
        return indexIfNotExists(object, id, collection, null);
    }

    @SneakyThrows
    default CompletableFuture<Void> indexIfNotExists(Object object, Object id, Object collection, Instant timestamp) {
        return index(object, id, collection, timestamp, timestamp, Guarantee.STORED, true);
    }

    @SneakyThrows
    default CompletableFuture<Void> indexIfNotExists(Object object, Object id, Object collection, Instant begin,
                                                     Instant end) {
        return index(object, id, collection, begin, end, Guarantee.STORED, true);
    }

    default <T> CompletableFuture<Void> indexIfNotExists(Collection<? extends T> objects, Object collection) {
        return indexIfNotExists(objects, collection,
                                v -> getAnnotatedPropertyValue(v, EntityId.class).map(Object::toString)
                                        .orElseGet(() -> currentIdentityProvider().nextTechnicalId()));
    }

    default <T> CompletableFuture<Void> indexIfNotExists(Collection<? extends T> objects, Object collection,
                                                         Function<? super T, ?> idFunction) {
        return indexIfNotExists(objects, collection, idFunction, v -> null);
    }

    @SneakyThrows
    default <T> CompletableFuture<Void> indexIfNotExists(Collection<? extends T> objects, Object collection,
                                                         String idPath,
                                                         String timestampPath) {
        return index(objects, collection, idPath, timestampPath, timestampPath, Guarantee.STORED, true);
    }

    @SneakyThrows
    default <T> CompletableFuture<Void> indexIfNotExists(Collection<? extends T> objects, Object collection,
                                                         String idPath,
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

    @SneakyThrows
    default CompletableFuture<Void> bulkUpdate(Collection<? extends BulkUpdate> updates) {
        return bulkUpdate(updates, Guarantee.STORED);
    }

    CompletableFuture<Void> bulkUpdate(Collection<? extends BulkUpdate> updates, Guarantee guarantee);

    default Search search(@NonNull Object collection) {
        List<String> collections = (collection instanceof Collection<?> list ? list.stream() : Stream.of(collection))
                .map(this::determineCollection).toList();
        return search(SearchQuery.builder().collections(collections));
    }

    Search search(SearchQuery.Builder queryBuilder);

    <T> Optional<T> fetchDocument(Object id, Object collection);

    <T> Optional<T> fetchDocument(Object id, Object collection, Class<T> type);

    CompletableFuture<Void> deleteDocument(Object id, Object collection);

    CompletableFuture<Void> deleteCollection(Object collection);

    CompletableFuture<Void> createAuditTrail(Object collection, Duration retentionTime);

    default String determineCollection(@NonNull Object c) {
        return ReflectionUtils.ifClass(c) instanceof Class<?> type
                ? getSearchParameters(type).getCollection() : c.toString();
    }

    DocumentSerializer getSerializer();

}
