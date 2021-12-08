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
import lombok.SneakyThrows;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;

public interface DocumentStore {

    default void index(Object object, String collection) {
        index(object, currentIdentityProvider().nextTechnicalId(), collection);
    }

    default void index(Object object, String id, String collection) {
        index(object, id, collection, null);
    }

    @SneakyThrows
    default void index(Object object, String id, String collection, Instant timestamp) {
        index(object, id, collection, timestamp, timestamp, Guarantee.STORED, false).get();
    }

    @SneakyThrows
    default void index(Object object, String id, String collection, Instant begin, Instant end) {
        index(object, id, collection, begin, end, Guarantee.STORED, false).get();
    }

    CompletableFuture<Void> index(Object object, String id, String collection, Instant begin, Instant end,
                                  Guarantee guarantee, boolean ifNotExists);

    default <T> void index(Collection<? extends T> objects, String collection) {
        index(objects, collection, v -> currentIdentityProvider().nextTechnicalId());
    }

    default <T> void index(Collection<? extends T> objects, String collection, Function<? super T, String> idFunction) {
        index(objects, collection, idFunction, v -> null);
    }

    @SneakyThrows
    default <T> void index(Collection<? extends T> objects, String collection, @Nullable String idPath,
                           @Nullable String timestampPath) {
        index(objects, collection, idPath, timestampPath, timestampPath, Guarantee.STORED, false).get();
    }

    @SneakyThrows
    default <T> void index(Collection<? extends T> objects, String collection, @Nullable String idPath,
                           @Nullable String beginPath, @Nullable String endPath) {
        index(objects, collection, idPath, beginPath, endPath, Guarantee.STORED, false).get();
    }

    <T> CompletableFuture<Void> index(Collection<? extends T> objects, String collection, @Nullable String idPath,
                                      @Nullable String beginPath, @Nullable String endPath, Guarantee guarantee,
                                      boolean ifNotExists);

    @SneakyThrows
    default <T> void index(Collection<? extends T> objects, String collection, Function<? super T, String> idFunction,
                           Function<? super T, Instant> timestampFunction) {
        index(objects, collection, idFunction, timestampFunction, timestampFunction, Guarantee.STORED, false).get();
    }

    @SneakyThrows
    default <T> void index(Collection<? extends T> objects, String collection, Function<? super T, String> idFunction,
                           Function<? super T, Instant> beginFunction, Function<? super T, Instant> endFunction) {
        index(objects, collection, idFunction, beginFunction, endFunction, Guarantee.STORED, false).get();
    }

    <T> CompletableFuture<Void> index(Collection<? extends T> objects, String collection,
                                      Function<? super T, String> idFunction,
                                      Function<? super T, Instant> beginFunction,
                                      Function<? super T, Instant> endFunction, Guarantee guarantee,
                                      boolean ifNotExists);

    default void indexIfNotExists(Object object, String collection) {
        indexIfNotExists(object, currentIdentityProvider().nextTechnicalId(), collection);
    }

    default void indexIfNotExists(Object object, String id, String collection) {
        indexIfNotExists(object, id, collection, null);
    }

    @SneakyThrows
    default void indexIfNotExists(Object object, String id, String collection, Instant timestamp) {
        index(object, id, collection, timestamp, timestamp, Guarantee.STORED, true).get();
    }

    @SneakyThrows
    default void indexIfNotExists(Object object, String id, String collection, Instant begin, Instant end) {
        index(object, id, collection, begin, end, Guarantee.STORED, true).get();
    }

    default <T> void indexIfNotExists(Collection<? extends T> objects, String collection) {
        indexIfNotExists(objects, collection, v -> currentIdentityProvider().nextTechnicalId());
    }

    default <T> void indexIfNotExists(Collection<? extends T> objects, String collection,
                                      Function<? super T, String> idFunction) {
        indexIfNotExists(objects, collection, idFunction, v -> null);
    }

    @SneakyThrows
    default <T> void indexIfNotExists(Collection<? extends T> objects, String collection, @Nullable String idPath,
                                      @Nullable String timestampPath) {
        index(objects, collection, idPath, timestampPath, timestampPath, Guarantee.STORED, true).get();
    }

    @SneakyThrows
    default <T> void indexIfNotExists(Collection<? extends T> objects, String collection, @Nullable String idPath,
                                      @Nullable String beginPath, @Nullable String endPath) {
        index(objects, collection, idPath, beginPath, endPath, Guarantee.STORED, true).get();
    }

    @SneakyThrows
    default <T> void indexIfNotExists(Collection<? extends T> objects, String collection,
                                      Function<? super T, String> idFunction,
                                      Function<? super T, Instant> timestampFunction) {
        index(objects, collection, idFunction, timestampFunction, timestampFunction, Guarantee.STORED, true).get();
    }

    @SneakyThrows
    default <T> void indexIfNotExists(Collection<? extends T> objects, String collection,
                                      Function<? super T, String> idFunction,
                                      Function<? super T, Instant> beginFunction,
                                      Function<? super T, Instant> endFunction) {
        index(objects, collection, idFunction, beginFunction, endFunction, Guarantee.STORED, true).get();
    }

    CompletableFuture<Void> bulkUpdate(Collection<BulkUpdate> updates, Guarantee guarantee);

    @SneakyThrows
    default void bulkUpdate(Collection<BulkUpdate> updates) {
        bulkUpdate(updates, Guarantee.STORED).get();
    }

    default Search search(String... collections) {
        return search(SearchQuery.builder().collections(Arrays.asList(collections)));
    }

    Search search(SearchQuery.Builder queryBuilder);

    <T> Optional<T> fetchDocument(String id, String collection);

    <T> Optional<T> fetchDocument(String id, String collection, Class<T> type);

    CompletableFuture<Void> deleteDocument(String id, String collection);

    CompletableFuture<Void> deleteCollection(String collection);

    CompletableFuture<Void> createAuditTrail(String collection, Duration retentionTime);

    DocumentSerializer getSerializer();

}
