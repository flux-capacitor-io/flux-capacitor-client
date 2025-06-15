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

package io.fluxcapacitor.javaclient.persisting.search;

import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.DocumentStats.FieldStats;
import io.fluxcapacitor.common.api.search.FacetStats;
import io.fluxcapacitor.common.api.search.Group;
import io.fluxcapacitor.common.api.search.SearchHistogram;
import io.fluxcapacitor.common.api.search.constraints.AllConstraint;
import io.fluxcapacitor.common.api.search.constraints.AnyConstraint;
import io.fluxcapacitor.common.api.search.constraints.BetweenConstraint;
import io.fluxcapacitor.common.api.search.constraints.ExistsConstraint;
import io.fluxcapacitor.common.api.search.constraints.FacetConstraint;
import io.fluxcapacitor.common.api.search.constraints.LookAheadConstraint;
import io.fluxcapacitor.common.api.search.constraints.MatchConstraint;
import io.fluxcapacitor.common.api.search.constraints.NotConstraint;
import io.fluxcapacitor.common.api.search.constraints.QueryConstraint;
import io.fluxcapacitor.javaclient.FluxCapacitor;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Fluent interface for building and executing document search queries in Flux Capacitor.
 * <p>
 * A {@code Search} instance is typically obtained via {@code FluxCapacitor.search("collectionName")} and can be
 * configured using a combination of time-based constraints, field constraints, sorting rules, pagination, and content
 * selection.
 * <p>
 * The search is only executed when a terminal operation like {@code fetch(...)} or {@code stream()} is invoked.
 * <p>
 * Supported operations include:
 * <ul>
 *   <li>Time-based filtering (e.g. {@link #since(Instant)}, {@link #inLast(Duration)})</li>
 *   <li>Content-based filtering (e.g. {@link #match(Object, String...)}, {@link #query(String, String...)})</li>
 *   <li>Sorting and pagination (e.g. {@link #sortByTimestamp()}, {@link #skip(Integer)})</li>
 *   <li>Aggregation and facets (e.g. {@link #aggregate(String...)}, {@link #facetStats()})</li>
 *   <li>Streaming and fetching results (e.g. {@link #stream()}, {@link #fetch(int)})</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * List<MyDocument> results = FluxCapacitor.search("myCollection")
 *     .inLast(Duration.ofDays(30))
 *     .match("searchTerm", "title", "description")
 *     .sortByTimestamp(true)
 *     .fetch(50);
 * }</pre>
 *
 * @see FluxCapacitor#search
 */
public interface Search {
    /**
     * The default number of records to fetch in a single batch during search operations. Primarily used in streaming
     * and batch-fetching methods to control the size of each data retrieval operation.
     * <p>
     * A higher value increases the data fetch per operation, potentially reducing the number of retrievals but
     * consuming more memory. A lower value minimizes memory usage but may require more network or database calls for
     * large datasets.
     */
    int defaultFetchSize = 10_000;

    /*
        Timing
     */

    /**
     * Filters documents with timestamps since the given start time (inclusive).
     */
    default Search since(Instant start) {
        return since(start, true);
    }

    /**
     * Filters documents with timestamps since the given start time.
     *
     * @param inclusive whether the start boundary is inclusive
     */
    Search since(Instant start, boolean inclusive);

    /**
     * Filters documents with timestamps strictly before the given end time.
     */
    default Search before(Instant endExclusive) {
        return before(endExclusive, false);
    }

    /**
     * Filters documents with timestamps before the given time.
     *
     * @param inclusive whether the end boundary is inclusive
     */
    Search before(Instant end, boolean inclusive);

    /**
     * Filters out documents older than the given duration.
     */
    default Search beforeLast(Duration period) {
        return before(FluxCapacitor.currentTime().minus(period));
    }

    /**
     * Filters documents within the last given duration (e.g., last 7 days).
     */
    default Search inLast(Duration period) {
        return since(FluxCapacitor.currentTime().minus(period));
    }

    /**
     * Filters documents within the given time range.
     */
    default Search inPeriod(Instant start, Instant endExclusive) {
        return inPeriod(start, true, endExclusive, false);
    }

    /**
     * Filters documents within a specified time range.
     */
    Search inPeriod(Instant start, boolean startInclusive, Instant end, boolean endInclusive);

    /*
        Other constraints
     */

    /**
     * Adds a full-text lookahead constraint using the specified phrase.
     */
    default Search lookAhead(String phrase, String... paths) {
        return constraint(LookAheadConstraint.lookAhead(phrase, paths));
    }

    /**
     * Adds a full-text search constraint for the given phrase.
     */
    default Search query(String phrase, String... paths) {
        return constraint(QueryConstraint.query(phrase, paths));
    }

    /**
     * Adds an equality match constraint for the given value across one or more paths.
     */
    default Search match(Object constraint, String... paths) {
        return match(constraint, false, paths);
    }

    /**
     * Adds a match constraint, optionally enforcing strict equality.
     */
    default Search match(Object constraint, boolean strict, String... paths) {
        return constraint(MatchConstraint.match(constraint, strict, paths));
    }

    /**
     * Matches the value of a named facet.
     */
    default Search matchFacet(String name, Object value) {
        return constraint(FacetConstraint.matchFacet(name, value));
    }

    /**
     * Matches a metadata key to a value.
     */
    default Search matchMetadata(String key, Object value) {
        return matchFacet("$metadata/" + key, value);
    }

    /**
     * Constrains the search to documents that have any of the given paths.
     */
    default Search anyExist(String... paths) {
        return constraint(ExistsConstraint.exists(paths));
    }

    /**
     * Adds a lower-bound constraint for a field.
     */
    default Search atLeast(Number min, String path) {
        return between(min, null, path);
    }

    /**
     * Adds an upper-bound constraint for a field.
     */
    default Search below(Number max, String path) {
        return between(null, max, path);
    }

    /**
     * Adds a numeric range constraint.
     */
    default Search between(Number min, Number maxExclusive, String path) {
        return constraint(BetweenConstraint.between(min, maxExclusive, path));
    }

    /**
     * Combines multiple constraints using a logical AND.
     */
    default Search all(Constraint... constraints) {
        return constraint(AllConstraint.all(constraints));
    }

    /**
     * Combines multiple constraints using a logical OR.
     */
    default Search any(Constraint... constraints) {
        return constraint(AnyConstraint.any(constraints));
    }

    /**
     * Negates a constraint using a logical NOT.
     */
    default Search not(Constraint constraint) {
        return constraint(NotConstraint.not(constraint));
    }

    /**
     * Adds one or more custom constraints to the search using a logical AND.
     */
    Search constraint(Constraint... constraints);

    /*
        Sorting
     */

    /**
     * Sorts results by timestamp in ascending order.
     */
    default Search sortByTimestamp() {
        return sortByTimestamp(false);
    }

    /**
     * Sorts results by timestamp.
     *
     * @param descending whether to sort in descending order
     */
    Search sortByTimestamp(boolean descending);

    /**
     * Sorts results by full-text relevance score.
     */
    Search sortByScore();

    /**
     * Sorts results by a specific document field.
     */
    default Search sortBy(String path) {
        return sortBy(path, false);
    }

    /**
     * Sorts results by a field, with control over the sort direction.
     */
    Search sortBy(String path, boolean descending);

    /*
        Content filtering
     */

    /**
     * Excludes specific fields from the returned documents.
     */
    Search exclude(String... paths);

    /**
     * Includes only the specified fields in the returned documents.
     */
    Search includeOnly(String... paths);

    /*
        Pagination
     */

    /**
     * Skips the first N results.
     */
    Search skip(Integer n);

    /*
        Execution
     */

    /**
     * Fetches up to the given number of matching documents and deserializes them to the stored type. Returns the
     * deserialized values as instances of type {@code T}.
     */
    <T> List<T> fetch(int maxSize);


    /**
     * Fetches up to the given number of documents and deserializes them to the specified type.
     */
    <T> List<T> fetch(int maxSize, Class<T> type);

    /**
     * Fetches all matching documents and deserializes each to its stored type. Returns the deserialized values as
     * instances of type {@code T}.
     */
    default <T> List<T> fetchAll() {
        return this.<T>stream().collect(toList());
    }

    /**
     * Fetches all matching documents and deserializes them to the specified type.
     */
    default <T> List<T> fetchAll(Class<T> type) {
        return this.stream(type).collect(toList());
    }

    /**
     * Fetches the first matching document if available and deserializes it to the stored type. Returns the deserialized
     * value as an optional instance of type {@code T}.
     */
    default <T> Optional<T> fetchFirst() {
        return this.<T>fetch(1).stream().findFirst();
    }

    /**
     * Fetches the first matching document if available and deserializes it as an optional value of the specified type.
     */
    default <T> Optional<T> fetchFirst(Class<T> type) {
        return this.fetch(1, type).stream().findFirst();
    }

    /**
     * Fetches the first matching document if available and deserializes it to the stored type. Returns the deserialized
     * value as an instance of type {@code T}.
     */
    default <T> T fetchFirstOrNull() {
        return this.<T>fetchFirst().orElse(null);
    }

    /**
     * Fetches the first matching document if available and deserializes it to the specified type.
     */
    default <T> T fetchFirstOrNull(Class<T> type) {
        return this.fetch(1, type).stream().findFirst().orElse(null);
    }

    /**
     * Streams matching values, deserializing each to the stored type. Documents will typically be fetched in batches
     * from the backing store. For the {@link DefaultDocumentStore default implementation}, the fetch size is 10,000.
     */
    default <T> Stream<T> stream() {
        return this.<T>streamHits().map(SearchHit::getValue);
    }

    /**
     * Streams matching values, deserializing each to the stored type. Documents will be fetched in batches of size
     * {@code fetchSize} from the backing store.
     */
    default <T> Stream<T> stream(int fetchSize) {
        return this.<T>streamHits(fetchSize).map(SearchHit::getValue);
    }

    /**
     * Streams matching values, deserializing each to the specified type. Documents will typically be fetched in batches
     * from the backing store. For the {@link DefaultDocumentStore default implementation}, the fetch size is 10,000.
     */
    default <T> Stream<T> stream(Class<T> type) {
        return this.streamHits(type).map(SearchHit::getValue);
    }

    /**
     * Streams matching values, deserializing each to the specified type. Documents will be fetched in batches of size
     * {@code fetchSize} from the backing store.
     */
    default <T> Stream<T> stream(Class<T> type, int fetchSize) {
        return this.streamHits(type, fetchSize).map(SearchHit::getValue);
    }

    /**
     * Streams raw search hits (document + metadata). Documents will typically be fetched in batches from the backing
     * store. For the {@link DefaultDocumentStore default implementation}, the fetch size is 10,000.
     */
    <T> Stream<SearchHit<T>> streamHits();

    /**
     * Streams raw search hits (document + metadata). Documents will be fetched in batches of size {@code fetchSize}
     * from the backing store. For the {@link DefaultDocumentStore default implementation}, the fetch size is 10,000.
     */
    <T> Stream<SearchHit<T>> streamHits(int fetchSize);

    /**
     * Streams raw search hits (document + metadata). Documents will be fetched in batches of size {@code fetchSize}
     * from the backing store. For the {@link DefaultDocumentStore default implementation}, the fetch size is 10,000.
     */
    <T> Stream<SearchHit<T>> streamHits(Class<T> type);

    /**
     * Streams raw search hits (document + metadata). Documents will be fetched in batches of size {@code fetchSize}
     * from the backing store. For the {@link DefaultDocumentStore default implementation}, the fetch size is 10,000.
     */
    <T> Stream<SearchHit<T>> streamHits(Class<T> type, int fetchSize);

    /*
        Aggregation
     */

    /**
     * Computes a histogram for the timestamp distribution of matching documents.
     */
    SearchHistogram fetchHistogram(int resolution, int maxSize);

    /**
     * Groups search results by field(s) and supports aggregations.
     */
    GroupSearch groupBy(String... paths);

    /**
     * Returns the number of matching documents.
     */
    default Long count() {
        return aggregate().values().stream().findFirst().map(FieldStats::getCount).orElse(0L);
    }

    /**
     * Returns field statistics for one or more fields.
     */
    default Map<String, FieldStats> aggregate(String... fields) {
        return groupBy().aggregate(fields).getOrDefault(Group.of(), Collections.emptyMap());
    }

    /**
     * Returns facet statistics for the current search.
     */
    List<FacetStats> facetStats();

    /*
        Deletion
     */

    /**
     * Deletes all matching documents in the current search.
     */
    CompletableFuture<Void> delete();
}
