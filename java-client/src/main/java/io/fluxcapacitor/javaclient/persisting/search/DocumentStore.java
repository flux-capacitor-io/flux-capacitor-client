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
import io.fluxcapacitor.common.api.search.SearchQuery;
import lombok.SneakyThrows;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentClock;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;

public interface DocumentStore {

    default void index(Object object, String collection) {
        index(object, currentIdentityProvider().nextId(), collection);
    }

    default void index(Object object, String id, String collection) {
        index(object, id, collection, currentClock().instant());
    }

    @SneakyThrows
    default void index(Object object, String id, String collection, Instant timestamp) {
        index(object, id, collection, timestamp, Guarantee.STORED).get();
    }

    CompletableFuture<Void> index(Object object, String id, String collection, Instant timestamp, Guarantee guarantee);

    default <T> void index(Collection<? extends T> objects, String collection) {
        index(objects, collection, v -> currentIdentityProvider().nextId());
    }

    default <T> void index(Collection<? extends T> objects, String collection, Function<? super T, String> idFunction) {
        index(objects, collection, idFunction, v -> currentClock().instant());
    }

    @SneakyThrows
    default <T> void index(Collection<? extends T> objects, String collection, @Nullable String idPath,
                           @Nullable String timestampPath) {
        index(objects, collection, idPath, timestampPath, Guarantee.STORED).get();
    }

    <T> CompletableFuture<Void> index(Collection<? extends T> objects, String collection, @Nullable String idPath,
                                      @Nullable String timestampPath, Guarantee guarantee);

    @SneakyThrows
    default <T> void index(Collection<? extends T> objects, String collection, Function<? super T, String> idFunction,
                           Function<? super T, Instant> timestampFunction) {
        index(objects, collection, idFunction, timestampFunction, Guarantee.STORED).get();
    }

    <T> CompletableFuture<Void> index(Collection<? extends T> objects, String collection,
                                      Function<? super T, String> idFunction,
                                      Function<? super T, Instant> timestampFunction, Guarantee guarantee);

    default Search search(String collection) {
        return search(SearchQuery.builder().collection(collection));
    }

    Search search(SearchQuery.Builder queryBuilder);

    void deleteDocument(String collection, String id);

    void deleteCollection(String collection);

    void createAuditTrail(String collection, Duration retentionTime);

    DocumentSerializer getSerializer();

}
