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

package io.fluxcapacitor.testserver.endpoints;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.VoidResult;
import io.fluxcapacitor.common.api.search.BulkUpdate;
import io.fluxcapacitor.common.api.search.BulkUpdateDocuments;
import io.fluxcapacitor.common.api.search.CreateAuditTrail;
import io.fluxcapacitor.common.api.search.DeleteCollection;
import io.fluxcapacitor.common.api.search.DeleteDocumentById;
import io.fluxcapacitor.common.api.search.DeleteDocuments;
import io.fluxcapacitor.common.api.search.DocumentUpdate;
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
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import io.fluxcapacitor.testserver.Handle;
import io.fluxcapacitor.testserver.WebsocketEndpoint;
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
    public VoidResult handle(IndexDocuments request) throws Exception {
        try {
            CompletableFuture<?> future = store.index(request.getDocuments(), request.getGuarantee(), request.isIfNotExists());
            if (request.getGuarantee().compareTo(Guarantee.STORED) >= 0) {
                future.get();
            }
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
        }
        return request.getGuarantee().compareTo(Guarantee.STORED) >= 0 ? new VoidResult(request.getRequestId()) : null;
    }

    @Handle
    public VoidResult handle(BulkUpdateDocuments request) throws Exception {
        Map<BulkUpdate.Type, List<DocumentUpdate>> updatesByType =
                request.getUpdates().stream().filter(Objects::nonNull)
                        .collect(toMap(a -> format("%s_%s", a.getCollection(), a.getId()), identity(), (a, b) -> b))
                        .values().stream()
                        .collect(groupingBy(DocumentUpdate::getType));
        try {
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

            if (request.getGuarantee().compareTo(Guarantee.STORED) >= 0) {
                for (CompletableFuture<?> result : results) {
                    result.get();
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
        }
        return request.getGuarantee().compareTo(Guarantee.STORED) >= 0 ? new VoidResult(request.getRequestId()) : null;
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
    public GetDocumentStatsResult handle(GetDocumentStats request) {
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
    public GetDocumentResult handle(GetDocument request) {
        try {
            return new GetDocumentResult(request.getRequestId(), store.fetch(request).orElse(null));
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
            return new GetDocumentResult(request.getRequestId(), null);
        }
    }

    @Handle
    public VoidResult handle(DeleteDocuments request) throws Exception {
        try {
            CompletableFuture<?> future = store.delete(request.getQuery(), request.getGuarantee());
            if (request.getGuarantee().compareTo(Guarantee.STORED) >= 0) {
                future.get();
            }
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
        }
        return request.getGuarantee().compareTo(Guarantee.STORED) >= 0 ? new VoidResult(request.getRequestId()) : null;
    }

    @Handle
    public VoidResult handle(DeleteDocumentById request) throws Exception {
        try {
            store.delete(request.getId(), request.getCollection(), request.getGuarantee());
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
        }
        return new VoidResult(request.getRequestId());
    }

    @Handle
    public VoidResult handle(DeleteCollection request) throws Exception {
        try {
            store.deleteCollection(request.getCollection());
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
        }
        return new VoidResult(request.getRequestId());
    }

    @Handle
    public VoidResult handle(CreateAuditTrail request) throws Exception {
        try {
            store.createAuditTrail(request);
        } catch (Exception e) {
            log.error("Failed to handle {}", request, e);
        }
        return new VoidResult(request.getRequestId());
    }


    @Override
    public String toString() {
        return "SearchEndpoint";
    }
}
