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
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.api.BooleanResult;
import io.fluxcapacitor.common.api.search.*;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;
import jakarta.websocket.ClientEndpoint;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * WebSocket-based implementation of the {@link SearchClient} that connects to the Flux platform.
 * <p>
 * All operations (indexing, searching, deletion, statistics, etc.) are executed via a WebSocket protocol
 * using a standardized API. This is the default production implementation used in deployed applications.
 * <p>
 * Requires an active connection to the Flux platform's search module.
 *
 * @see WebSocketClient
 */
@ClientEndpoint
public class WebSocketSearchClient extends AbstractWebsocketClient implements SearchClient {

    public WebSocketSearchClient(String endPointUrl, WebSocketClient client) {
        this(URI.create(endPointUrl), client);
    }

    public WebSocketSearchClient(URI endpointUri, WebSocketClient client) {
        this(endpointUri, client, true);
    }

    public WebSocketSearchClient(URI endpointUri, WebSocketClient client, boolean sendMetrics) {
        super(endpointUri, client, sendMetrics, client.getClientConfig().getSearchSessions());
    }

    @Override
    public CompletableFuture<Void> index(List<SerializedDocument> documents, Guarantee guarantee, boolean ifNotExists) {
        return sendCommand(new IndexDocuments(documents, ifNotExists, guarantee));
    }

    @Override
    public CompletableFuture<Void> bulkUpdate(Collection<DocumentUpdate> batch, Guarantee guarantee) {
        return sendCommand(new BulkUpdateDocuments(batch, guarantee));
    }

    @Override
    public Stream<SearchHit<SerializedDocument>> search(SearchDocuments searchDocuments, int fetchSize) {
        AtomicInteger count = new AtomicInteger();
        Integer maxSize = searchDocuments.getMaxSize();
        int maxFetchSize = maxSize == null ? fetchSize : Math.min(maxSize, fetchSize);
        SearchDocuments request = searchDocuments.toBuilder().maxSize(maxFetchSize).build();
        Stream<SerializedDocument> documentStream = ObjectUtils.<SearchDocumentsResult>iterate(
                        sendAndWait(request),
                        result -> sendAndWait(request.toBuilder().maxSize(
                                        maxSize == null ? maxFetchSize : Math.min(maxSize - count.get(), maxFetchSize))
                                                      .lastHit(result.lastMatch()).build()),
                        result -> result.size() < maxFetchSize
                                || (maxSize != null && count.addAndGet(result.size()) >= maxSize))
                .flatMap(r -> r.getMatches().stream());
        if (maxSize != null) {
            documentStream = documentStream.limit(maxSize);
        }
        return documentStream.map(SearchHit::fromDocument);
    }

    @Override
    public boolean documentExists(HasDocument request) {
        return this.<BooleanResult>sendAndWait(request).isSuccess();
    }

    @Override
    public Optional<SerializedDocument> fetch(GetDocument request) {
        return Optional.ofNullable(this.<GetDocumentResult>sendAndWait(request).getDocument());
    }

    @Override
    public Collection<SerializedDocument> fetch(GetDocuments request) {
        return this.<GetDocumentsResult>sendAndWait(request).getDocuments();
    }

    @Override
    public List<DocumentStats> fetchStatistics(SearchQuery query, List<String> fields, List<String> groupBy) {
        GetDocumentStatsResult result = sendAndWait(new GetDocumentStats(query, fields, groupBy));
        return result.getDocumentStats();
    }

    @Override
    public SearchHistogram fetchHistogram(GetSearchHistogram request) {
        GetSearchHistogramResult result = sendAndWait(request);
        return result.getHistogram();
    }

    @Override
    public List<FacetStats> fetchFacetStats(SearchQuery query) {
        GetFacetStatsResult result = sendAndWait(new GetFacetStats(query));
        return result.getStats();
    }

    @Override
    public CompletableFuture<Void> delete(SearchQuery query, Guarantee guarantee) {
        return sendCommand(new DeleteDocuments(query, guarantee));
    }

    @Override
    public CompletableFuture<Void> delete(String documentId, String collection, Guarantee guarantee) {
        return sendCommand(new DeleteDocumentById(collection, documentId, guarantee));
    }

    @Override
    public CompletableFuture<Void> deleteCollection(String collection, Guarantee guarantee) {
        return sendCommand(new DeleteCollection(collection, guarantee));
    }

    @Override
    public CompletableFuture<Void> createAuditTrail(CreateAuditTrail request) {
        return sendCommand(request);
    }
}
