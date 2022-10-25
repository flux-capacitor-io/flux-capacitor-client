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
import io.fluxcapacitor.common.ObjectUtils;
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

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.Awaitable.fromFuture;

@ClientEndpoint
public class WebSocketSearchClient extends AbstractWebsocketClient implements SearchClient {
    public static int maxFetchSize = 10_000;

    public WebSocketSearchClient(String endPointUrl, WebSocketClient.ClientConfig clientConfig) {
        this(URI.create(endPointUrl), clientConfig);
    }

    public WebSocketSearchClient(URI endpointUri, WebSocketClient.ClientConfig clientConfig) {
        this(endpointUri, clientConfig, true);
    }

    public WebSocketSearchClient(URI endpointUri, WebSocketClient.ClientConfig clientConfig, boolean sendMetrics) {
        super(endpointUri, clientConfig, sendMetrics, clientConfig.getSearchSessions());
    }

    @Override
    public Awaitable index(List<Document> documents, Guarantee guarantee, boolean ifNotExists) {
        return fromFuture(send(new IndexDocuments(
                documents.stream().map(SerializedDocument::new).collect(Collectors.toList()), ifNotExists, guarantee)));
    }

    @Override
    public Awaitable bulkUpdate(Collection<SerializedDocumentUpdate> batch, Guarantee guarantee) {
        return fromFuture(send(new BulkUpdateDocuments(batch, guarantee)));
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
        return this.<GetDocumentStatsResult>sendAndWait(
                new GetDocumentStats(query, fields, groupBy)).getDocumentStats();
    }

    @Override
    public SearchHistogram fetchHistogram(GetSearchHistogram request) {
        return this.<GetSearchHistogramResult>sendAndWait(request).getHistogram();
    }

    @Override
    public Awaitable delete(SearchQuery query, Guarantee guarantee) {
        return fromFuture(send(new DeleteDocuments(query, guarantee)));
    }

    @Override
    public Awaitable delete(String documentId, String collection, Guarantee guarantee) {
        return fromFuture(send(new DeleteDocumentById(collection, documentId, guarantee)));
    }

    @Override
    public Awaitable deleteCollection(String collection, Guarantee guarantee) {
        return fromFuture(send(new DeleteCollection(collection, guarantee)));
    }

    @Override
    public Awaitable createAuditTrail(CreateAuditTrail request) {
        return fromFuture(send(request));
    }
}
