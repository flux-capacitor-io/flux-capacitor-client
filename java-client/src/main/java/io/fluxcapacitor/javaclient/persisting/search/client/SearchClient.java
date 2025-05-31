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
import io.fluxcapacitor.common.api.search.HasDocument;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import io.fluxcapacitor.common.api.search.SearchHistogram;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.javaclient.persisting.search.DocumentSerializer;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Low-level interface for interacting with a search and indexing service in Flux.
 * <p>
 * The {@code SearchClient} operates exclusively on serialized forms of documents
 * (see {@link SerializedDocument}) and search-related requests. It is the primary interface used by
 * internal components like {@link io.fluxcapacitor.javaclient.persisting.search.DocumentStore} to
 * execute actual search operations against a backing implementation (e.g., Flux platform or
 * an in-memory search engine for testing).
 *
 * @see SerializedDocument
 * @see io.fluxcapacitor.javaclient.persisting.search.DocumentStore
 * @see DocumentSerializer
 */
public interface SearchClient extends AutoCloseable {

    /**
     * Indexes a list of serialized documents into the search engine.
     *
     * @param documents     the documents to index
     * @param guarantee     delivery guarantee (see {@link Guarantee})
     * @param ifNotExists   if {@code true}, only index documents that do not already exist
     * @return a future that completes when the operation is done
     */
    CompletableFuture<Void> index(List<SerializedDocument> documents, Guarantee guarantee, boolean ifNotExists);

    /**
     * Executes a streaming search query using the given criteria and fetch size.
     *
     * @param searchDocuments the search parameters and query
     * @param fetchSize       the number of results to fetch per page
     * @return a stream of search hits matching the query
     */
    Stream<SearchHit<SerializedDocument>> search(SearchDocuments searchDocuments, int fetchSize);

    /**
     * Checks whether a document with the given criteria exists.
     *
     * @param request an object describing the document (e.g., id and collection)
     * @return {@code true} if the document exists, {@code false} otherwise
     */
    boolean documentExists(HasDocument request);

    /**
     * Fetches a single serialized document matching the given request.
     *
     * @param request an object describing the document to retrieve
     * @return an optional containing the document, if found
     */
    Optional<SerializedDocument> fetch(GetDocument request);

    /**
     * Deletes documents matching a given query.
     *
     * @param query      the search query specifying which documents to delete
     * @param guarantee  delivery guarantee
     * @return a future that completes when the deletion has been performed
     */
    CompletableFuture<Void> delete(SearchQuery query, Guarantee guarantee);

    /**
     * Deletes a document by its unique id and collection name.
     *
     * @param documentId the document id
     * @param collection the collection to delete from
     * @param guarantee  delivery guarantee
     * @return a future that completes when the deletion has been performed
     */
    CompletableFuture<Void> delete(String documentId, String collection, Guarantee guarantee);

    /**
     * Configures Flux to use a search collection as a searchable audit trail.
     *
     * @param request a request object specifying the collection to use as an audit trail and retention configuration
     * @return a future that completes when the audit trail has been created.
     */
    CompletableFuture<Void> createAuditTrail(CreateAuditTrail request);

    /**
     * Deletes an entire document collection and all its contents.
     *
     * @param collection the name of the collection to delete
     * @return a future that completes when the collection has been deleted
     */
    default CompletableFuture<Void> deleteCollection(String collection) {
        return deleteCollection(collection, Guarantee.STORED);
    }

    /**
     * Deletes an entire document collection and all its contents.
     *
     * @param collection the name of the collection to delete
     * @param guarantee  delivery guarantee
     * @return a future that completes when the collection has been deleted
     */
    CompletableFuture<Void> deleteCollection(String collection, Guarantee guarantee);

    /**
     * Retrieves search statistics (counts, averages, etc.) over matching documents.
     *
     * @param query   the query to filter documents
     * @param fields  the fields to compute statistics for
     * @param groupBy field names used to group statistics
     * @return a list of {@link DocumentStats}
     */
    List<DocumentStats> fetchStatistics(SearchQuery query, List<String> fields, List<String> groupBy);

    /**
     * Fetches a histogram (bucketed time-series view) for documents matching the query.
     *
     * @param request the histogram query parameters
     * @return a {@link SearchHistogram} representing the result
     */
    SearchHistogram fetchHistogram(GetSearchHistogram request);

    /**
     * Retrieves facet statistics (i.e., value counts) for a given query.
     *
     * @param query the query to match documents against
     * @return a list of facet statistics
     */
    List<FacetStats> fetchFacetStats(SearchQuery query);

    /**
     * Performs a batch update on a set of documents.
     *
     * @param updates    the update operations to perform
     * @param guarantee  delivery guarantee
     * @return a future that completes when the updates have been applied
     */
    CompletableFuture<Void> bulkUpdate(Collection<DocumentUpdate> updates, Guarantee guarantee);

    /**
     * Closes any underlying resources.
     */
    @Override
    void close();
}
