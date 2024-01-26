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
import io.fluxcapacitor.common.api.search.CreateAuditTrail;
import io.fluxcapacitor.common.api.search.DocumentStats;
import io.fluxcapacitor.common.api.search.DocumentUpdate;
import io.fluxcapacitor.common.api.search.FacetStats;
import io.fluxcapacitor.common.api.search.GetDocument;
import io.fluxcapacitor.common.api.search.GetSearchHistogram;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import io.fluxcapacitor.common.api.search.SearchHistogram;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public interface SearchClient extends AutoCloseable {

    CompletableFuture<Void> index(List<SerializedDocument> documents, Guarantee guarantee, boolean ifNotExists);

    Stream<SearchHit<SerializedDocument>> search(SearchDocuments searchDocuments, int fetchSize);

    Optional<SerializedDocument> fetch(GetDocument request);

    CompletableFuture<Void> delete(SearchQuery query, Guarantee guarantee);

    CompletableFuture<Void> delete(String documentId, String collection, Guarantee guarantee);

    CompletableFuture<Void> createAuditTrail(CreateAuditTrail request);

    default CompletableFuture<Void> deleteCollection(String collection) {
        return deleteCollection(collection, Guarantee.STORED);
    }

    CompletableFuture<Void> deleteCollection(String collection, Guarantee guarantee);

    List<DocumentStats> fetchStatistics(SearchQuery query, List<String> fields, List<String> groupBy);

    SearchHistogram fetchHistogram(GetSearchHistogram request);

    List<FacetStats> fetchFacetStats(SearchQuery query);

    CompletableFuture<Void> bulkUpdate(Collection<DocumentUpdate> updates, Guarantee guarantee);

    @Override
    void close();
}
