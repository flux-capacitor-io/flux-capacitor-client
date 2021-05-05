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
import io.fluxcapacitor.common.Backlog;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.api.QueryResult;
import io.fluxcapacitor.common.api.search.CreateAuditTrail;
import io.fluxcapacitor.common.api.search.DeleteCollection;
import io.fluxcapacitor.common.api.search.DeleteDocumentById;
import io.fluxcapacitor.common.api.search.DeleteDocuments;
import io.fluxcapacitor.common.api.search.DocumentStats;
import io.fluxcapacitor.common.api.search.GetDocumentStats;
import io.fluxcapacitor.common.api.search.GetDocumentStatsResult;
import io.fluxcapacitor.common.api.search.GetSearchHistogram;
import io.fluxcapacitor.common.api.search.GetSearchHistogramResult;
import io.fluxcapacitor.common.api.search.IndexDocuments;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import io.fluxcapacitor.common.api.search.SearchDocumentsResult;
import io.fluxcapacitor.common.api.search.SearchHistogram;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@ClientEndpoint
public class WebSocketSearchClient extends AbstractWebsocketClient implements SearchClient {
    private final Backlog<Document> backlog;

    public WebSocketSearchClient(String endPointUrl, WebSocketClient.Properties properties) {
        this(URI.create(endPointUrl), properties);
    }

    public WebSocketSearchClient(URI endpointUri, WebSocketClient.Properties properties) {
        super(endpointUri, properties, true, properties.getSearchSessions());
        backlog = new Backlog<>(this::storeValues);
    }

    protected Awaitable storeValues(List<Document> documents) {
        return sendAndForget(new IndexDocuments(
                documents.stream().map(SerializedDocument::new).collect(Collectors.toList()), Guarantee.SENT));
    }

    @Override
    public Awaitable index(Document... documents) {
        return backlog.add(documents);
    }

    @Override
    public Awaitable index(List<Document> documents, Guarantee guarantee) {
        switch (guarantee) {
            case NONE:
                backlog.add(documents);
                return Awaitable.ready();
            case SENT:
                return backlog.add(documents);
            case STORED:
                CompletableFuture<QueryResult> future = send(new IndexDocuments(
                        documents.stream().map(SerializedDocument::new).collect(Collectors.toList()), guarantee));
                return future::get;
            default:
                throw new UnsupportedOperationException("Unrecognized guarantee: " + guarantee);
        }
    }

    @Override
    public Stream<SearchHit<Document>> search(SearchQuery query, List<String> sorting, Integer maxSize) {
        AtomicInteger count = new AtomicInteger();
        int fetchBatchSize = maxSize == null ? 10_000 : Math.min(maxSize, 10_000);
        SearchDocuments request =
                SearchDocuments.builder().query(query).sorting(sorting).maxSize(fetchBatchSize).build();
        Stream<SerializedDocument> documentStream = ObjectUtils.<SearchDocumentsResult>iterate(
                sendAndWait(request),
                result -> sendAndWait(request.toBuilder().maxSize(
                        maxSize == null ? fetchBatchSize : Math.min(maxSize - count.get(), fetchBatchSize))
                                              .lastHit(result.lastMatch()).build()),
                result -> result.size() < fetchBatchSize
                        || (maxSize != null && count.addAndGet(result.size()) >= maxSize))
                .flatMap(r -> r.getMatches().stream());
        if (maxSize != null) {
            documentStream = documentStream.limit(maxSize);
        }
        return documentStream.map(d -> new SearchHit<>(d.getId(), d.getCollection(), d.getTimestamp() == null
                ? null : Instant.ofEpochMilli(d.getTimestamp()), d::deserializeDocument));
    }

    @Override
    public List<DocumentStats> getStatistics(SearchQuery query, List<String> fields, List<String> groupBy) {
        GetDocumentStatsResult result = sendAndWait(new GetDocumentStats(query, fields, groupBy));
        return result.getDocumentStats();
    }

    @Override
    public SearchHistogram getHistogram(GetSearchHistogram request) {
        GetSearchHistogramResult result = sendAndWait(request);
        return result.getHistogram();
    }

    @Override
    public Awaitable delete(SearchQuery query, Guarantee guarantee) {
        DeleteDocuments request = new DeleteDocuments(query, guarantee);
        switch (guarantee) {
            case NONE:
                sendAndForget(request);
                return Awaitable.ready();
            case SENT:
                send(request);
                return Awaitable.ready();
            case STORED:
                CompletableFuture<QueryResult> future = send(request);
                return future::get;
            default:
                throw new UnsupportedOperationException("Unrecognized guarantee: " + guarantee);
        }
    }

    @Override
    public Awaitable delete(String collection, String documentId, Guarantee guarantee) {
        DeleteDocumentById request = new DeleteDocumentById(collection, documentId, guarantee);
        switch (guarantee) {
            case NONE:
                sendAndForget(request);
                return Awaitable.ready();
            case SENT:
                send(request);
                return Awaitable.ready();
            case STORED:
                CompletableFuture<QueryResult> future = send(request);
                return future::get;
            default:
                throw new UnsupportedOperationException("Unrecognized guarantee: " + guarantee);
        }
    }

    @Override
    public Awaitable deleteCollection(String collection) {
        CompletableFuture<QueryResult> future = send(new DeleteCollection(collection));
        return future::get;
    }

    @Override
    public Awaitable createAuditTrail(CreateAuditTrail request) {
        CompletableFuture<QueryResult> future = send(request);
        return future::get;
    }
}
