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
import io.fluxcapacitor.javaclient.FluxCapacitor;

import java.time.Duration;
import java.time.Instant;

public interface DocumentStore {

    default void index(Object object, String id, String collection) {
        index(object, id, FluxCapacitor.currentClock().instant(), collection, Guarantee.SENT);
    }

    default void index(Object object, String id, String collection, Guarantee guarantee) {
        index(object, id, FluxCapacitor.currentClock().instant(), collection, guarantee);
    }

    void index(Object object, String id, Instant timestamp, String collection, Guarantee guarantee);

    default Search search(String collection) {
        return search(SearchQuery.builder().collection(collection));
    }

    Search search(SearchQuery.Builder queryBuilder);

    void deleteDocument(String collection, String id);

    void deleteCollection(String collection);

    void createAuditTrail(String collection, Duration retentionTime);

    DocumentSerializer getSerializer();

}
