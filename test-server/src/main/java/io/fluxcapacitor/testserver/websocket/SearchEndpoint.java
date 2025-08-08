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

package io.fluxcapacitor.testserver.websocket;

import io.fluxcapacitor.common.api.search.*;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.api.search.BulkUpdate.Type.delete;
import static io.fluxcapacitor.common.api.search.BulkUpdate.Type.index;
import static io.fluxcapacitor.common.api.search.BulkUpdate.Type.indexIfNotExists;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@Slf4j
@AllArgsConstructor
public class SearchEndpoint extends WebsocketEndpoint {

    private final SearchClient store;

    @Handle
    CompletableFuture<Void> handle(IndexDocuments request) {
        return store.index(request.getDocuments(), request.getGuarantee(), request.isIfNotExists());
    }

    @Handle
    CompletableFuture<Void> handle(BulkUpdateDocuments request) {
        Map<BulkUpdate.Type, List<DocumentUpdate>> updatesByType =
                request.getUpdates().stream().filter(Objects::nonNull)
                        .collect(toMap(a -> format("%s_%s", a.getCollection(), a.getId()), identity(), (a, b) -> b))
                        .values().stream()
                        .collect(groupingBy(DocumentUpdate::getType));
        Collection<CompletableFuture<Void>> results = new ArrayList<>();
        ofNullable(updatesByType.get(index)).ifPresent(updates -> {
            var documents = updates.stream().map(DocumentUpdate::getObject).toList();
            results.add(store.index(documents, request.getGuarantee(), false));
        });
        ofNullable(updatesByType.get(indexIfNotExists)).ifPresent(updates -> {
            var documents = updates.stream().map(DocumentUpdate::getObject).toList();
            results.add(store.index(documents, request.getGuarantee(), true));
        });
        updatesByType.getOrDefault(delete, emptyList())
                .forEach(delete -> store.delete(delete.getId(), delete.getCollection(), request.getGuarantee()));
        return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new));
    }

    @Handle
    public SearchDocumentsResult handle(SearchDocuments request) {
        try {
            Stream<SearchHit<SerializedDocument>> result = store.search(request, -1);
            return new SearchDocumentsResult(request.getRequestId(), result.map(SearchHit::getValue).toList());
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
            return new SearchDocumentsResult(request.getRequestId(), emptyList());
        }
    }

    @Handle
    public GetSearchHistogramResult handle(GetSearchHistogram request) {
        try {
            return new GetSearchHistogramResult(request.getRequestId(), store.fetchHistogram(request));
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
            return new GetSearchHistogramResult(request.getRequestId(),
                                                new SearchHistogram(request.getQuery().getSince(),
                                                                    request.getQuery().getBefore(),
                                                                    emptyList()));
        }
    }

    @Handle
    GetDocumentStatsResult handle(GetDocumentStats request) {
        try {
            return new GetDocumentStatsResult(request.getRequestId(),
                                              store.fetchStatistics(request.getQuery(), request.getFields(),
                                                                    request.getGroupBy()));
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
            return new GetDocumentStatsResult(request.getRequestId(), emptyList());
        }
    }

    @Handle
    GetDocumentResult handle(GetDocument request) {
        try {
            return new GetDocumentResult(request.getRequestId(), store.fetch(request).orElse(null));
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
            return new GetDocumentResult(request.getRequestId(), null);
        }
    }

    @Handle
    GetDocumentsResult handle(GetDocuments request) {
        try {
            return new GetDocumentsResult(request.getRequestId(), store.fetch(request));
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
            return new GetDocumentsResult(request.getRequestId(), emptyList());
        }
    }

    @Handle
    CompletableFuture<Void> handle(DeleteDocuments request) {
        return store.delete(request.getQuery(), request.getGuarantee());
    }

    @Handle
    void handle(DeleteDocumentById request) {
        store.delete(request.getId(), request.getCollection(), request.getGuarantee());
    }

    @Handle
    void handle(DeleteCollection request) {
        store.deleteCollection(request.getCollection(), request.getGuarantee());
    }

    @Handle
    void handle(CreateAuditTrail request) {
        store.createAuditTrail(request);
    }

    @Handle
    GetFacetStatsResult handle(GetFacetStats request) {
        try {
            return new GetFacetStatsResult(request.getRequestId(), store.fetchFacetStats(request.getQuery()));
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
            return new GetFacetStatsResult(request.getRequestId(), emptyList());
        }
    }
}
