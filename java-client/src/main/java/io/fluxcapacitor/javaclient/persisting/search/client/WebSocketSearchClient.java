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
import io.fluxcapacitor.common.api.search.BulkUpdateDocuments;
import io.fluxcapacitor.common.api.search.CreateAuditTrail;
import io.fluxcapacitor.common.api.search.DeleteCollection;
import io.fluxcapacitor.common.api.search.DeleteDocumentById;
import io.fluxcapacitor.common.api.search.DeleteDocuments;
import io.fluxcapacitor.common.api.search.DocumentStats;
import io.fluxcapacitor.common.api.search.GetDocument;
import io.fluxcapacitor.common.api.search.GetDocumentResult;
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
import io.fluxcapacitor.common.api.search.SerializedDocumentUpdate;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;
import jakarta.websocket.ClientEndpoint;

import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.deduplicate;

@ClientEndpoint
public class WebSocketSearchClient extends AbstractWebsocketClient implements SearchClient {
    public static int maxFetchSize = 10_000;

    private final Backlog<Document> sendBacklog;
    private final Backlog<Document> sendIfNotExistsBacklog;
    private final Backlog<Document> storeBacklog;
    private final Backlog<Document> storeIfNotExistsBacklog;
    private final Backlog<SerializedDocumentUpdate> sendBatchBacklog;
    private final Backlog<SerializedDocumentUpdate> storeBatchBacklog;

    public WebSocketSearchClient(String endPointUrl, WebSocketClient.ClientConfig clientConfig) {
        this(URI.create(endPointUrl), clientConfig);
    }

    public WebSocketSearchClient(URI endpointUri, WebSocketClient.ClientConfig clientConfig) {
        this(endpointUri, clientConfig, true);
    }

    public WebSocketSearchClient(URI endpointUri, WebSocketClient.ClientConfig clientConfig, boolean sendMetrics) {
        super(endpointUri, clientConfig, sendMetrics, clientConfig.getSearchSessions());
        sendBacklog = new Backlog<>(documents -> sendValues(documents, false));
        sendIfNotExistsBacklog = new Backlog<>(documents -> sendValues(documents, true));
        storeBacklog = new Backlog<>(documents -> storeValues(documents, false));
        storeIfNotExistsBacklog = new Backlog<>(documents -> storeValues(documents, true));
        sendBatchBacklog = new Backlog<>(actions -> sendAndForget(new BulkUpdateDocuments(actions, Guarantee.SENT)));
        storeBatchBacklog = new Backlog<>(actions -> {
            sendAndWait(new BulkUpdateDocuments(actions, Guarantee.STORED));
            return Awaitable.ready();
        });
    }

    protected Awaitable sendValues(List<Document> documents, boolean ifNotExists) {
        return sendAndForget(new IndexDocuments(
                deduplicate(documents, Document.identityFunction).stream().map(SerializedDocument::new)
                        .collect(Collectors.toList()), ifNotExists, Guarantee.SENT));
    }

    protected Awaitable storeValues(List<Document> documents, boolean ifNotExists) {
        sendAndWait(new IndexDocuments(
                deduplicate(documents, Document.identityFunction).stream().map(SerializedDocument::new)
                        .collect(Collectors.toList()), ifNotExists, Guarantee.STORED));
        return Awaitable.ready();
    }

    @Override
    public Awaitable index(List<Document> documents, Guarantee guarantee, boolean ifNotExists) {
        switch (guarantee) {
            case NONE:
                Awaitable ignored = ifNotExists ? sendIfNotExistsBacklog.add(documents) : sendBacklog.add(documents);
                return Awaitable.ready();
            case SENT:
                return ifNotExists ? sendIfNotExistsBacklog.add(documents) : sendBacklog.add(documents);
            case STORED:
                return ifNotExists ? storeIfNotExistsBacklog.add(documents) : storeBacklog.add(documents);
            default:
                throw new UnsupportedOperationException("Unrecognized guarantee: " + guarantee);
        }
    }

    @Override
    public Awaitable bulkUpdate(Collection<SerializedDocumentUpdate> batch, Guarantee guarantee) {
        switch (guarantee) {
            case NONE:
                sendBatchBacklog.add(batch);
                return Awaitable.ready();
            case SENT:
                return sendBatchBacklog.add(batch);
            case STORED:
                return storeBatchBacklog.add(batch);
            default:
                throw new UnsupportedOperationException("Unrecognized guarantee: " + guarantee);
        }
    }

    @Override
    public Stream<SearchHit<Document>> search(SearchDocuments searchDocuments) {
        AtomicInteger count = new AtomicInteger();
        Integer maxSize = searchDocuments.getMaxSize();
        int fetchSize = maxSize == null ? maxFetchSize : Math.min(maxSize, maxFetchSize);
        SearchDocuments request = searchDocuments.toBuilder().maxSize(fetchSize).build();
        Stream<SerializedDocument> documentStream = ObjectUtils.<SearchDocumentsResult>iterate(
                        sendAndWait(request),
                        result -> sendAndWait(request.toBuilder().maxSize(
                                        maxSize == null ? fetchSize : Math.min(maxSize - count.get(), fetchSize))
                                                      .lastHit(result.lastMatch()).build()),
                        result -> result.size() < fetchSize
                                || (maxSize != null && count.addAndGet(result.size()) >= maxSize))
                .flatMap(r -> r.getMatches().stream());
        if (maxSize != null) {
            documentStream = documentStream.limit(maxSize);
        }
        return documentStream.map(d -> new SearchHit<>(d.getId(), d.getCollection(),
                                                       d.getTimestamp() == null ? null :
                                                               Instant.ofEpochMilli(d.getTimestamp()),
                                                       d.getEnd() == null ? null : Instant.ofEpochMilli(d.getEnd()),
                                                       d::deserializeDocument));
    }

    @Override
    public Optional<Document> fetch(GetDocument request) {
        return Optional.ofNullable(this.<GetDocumentResult>sendAndWait(request).getDocument())
                .map(SerializedDocument::deserializeDocument);
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
    public Awaitable delete(String documentId, String collection, Guarantee guarantee) {
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
