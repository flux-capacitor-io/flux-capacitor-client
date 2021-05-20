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

package io.fluxcapacitor.javaclient.persisting.search.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.search.*;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;

import java.util.List;
import java.util.stream.Stream;

public interface SearchClient extends AutoCloseable {

    Awaitable index(List<Document> documents, Guarantee guarantee, boolean ifNotExists);

    Stream<SearchHit<Document>> search(SearchDocuments searchDocuments);

    Awaitable delete(SearchQuery query, Guarantee guarantee);

    Awaitable delete(String documentId, String collection, Guarantee guarantee);

    Awaitable createAuditTrail(CreateAuditTrail request);

    Awaitable deleteCollection(String collection);

    List<DocumentStats> getStatistics(SearchQuery query, List<String> fields, List<String> groupBy);

    SearchHistogram getHistogram(GetSearchHistogram request);

    @Override
    void close();
}
