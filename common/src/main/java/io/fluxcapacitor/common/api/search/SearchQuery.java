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

package io.fluxcapacitor.common.api.search;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fluxcapacitor.common.api.search.constraints.AllConstraint;
import io.fluxcapacitor.common.search.Document;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A query for filtering documents in one or more search collections.
 * <p>
 * A {@code SearchQuery} is used to retrieve documents from the search index by applying a combination of time-based
 * constraints, collection filters, and field-level constraints.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * SearchQuery query = SearchQuery.builder()
 *     .collection("complaints")
 *     .since(Instant.parse("2023-01-01T00:00:00Z"))
 *     .before(Instant.now())
 *     .constraint(BetweenConstraint.atLeast(2, "priority"))
 *     .build();
 * }</pre>
 *
 * <h2>Fields</h2>
 * <ul>
 *     <li>{@code collections}: List of collection names to search in (required).</li>
 *     <li>{@code since, before}: Start and end timestamps to filter documents within a time window.</li>
 *     <li>{@code sinceExclusive, beforeInclusive}: Control boundary inclusivity for time filtering.</li>
 *     <li>{@code constraints}: A list of {@link Constraint} objects applied to fields within the document.</li>
 * </ul>
 *
 * <h2>Validation</h2>
 * The query must include at least one collection. An exception is thrown if none are specified.
 *
 * @see Constraint
 * @see SerializedDocument
 * @see io.fluxcapacitor.common.search.Document
 */
@Value
@lombok.Builder(toBuilder = true, builderClassName = "Builder")
public class SearchQuery {
    /**
     * Builder class for constructing instances of {@link SearchQuery}.
     */
    public static class Builder {
    }

    @JsonAlias("collections")
    @JsonProperty("collection")
    @Singular
    List<String> collections;
    Instant since, before;
    boolean sinceExclusive;
    boolean beforeInclusive;
    @Singular
    List<Constraint> constraints;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    @Accessors(fluent = true)
    Constraint decomposeConstraints = AllConstraint.all(getConstraints().stream().map(Constraint::decompose).collect(
            Collectors.toList()));

    @lombok.Builder(toBuilder = true, builderClassName = "Builder")
    @Jacksonized
    public SearchQuery(List<String> collections, Instant since, Instant before, boolean sinceExclusive,
                       boolean beforeInclusive, List<Constraint> constraints) {
        this.sinceExclusive = sinceExclusive;
        this.beforeInclusive = beforeInclusive;
        if (collections.isEmpty()) {
            throw new IllegalArgumentException("Collections should not be empty");
        }
        this.collections = collections;
        this.since = since;
        this.before = before;
        this.constraints = constraints;
    }

    public Instant getBefore() {
        return before == null || since == null || before.isAfter(since) ? before : since;
    }

    /**
     * Checks if the given serialized document matches the query's constraints and collection filters.
     *
     * @param d the serialized document to be checked; may be null. If null or if the collection of the document is not
     *          part of the query's collection filters, the method returns false.
     * @return true if the document meets the constraints and collection filters of the query, false otherwise.
     */
    public boolean matches(SerializedDocument d) {
        if (d == null || !collections.contains(d.getCollection())) {
            return false;
        }
        return matches(d.deserializeDocument());
    }

    @SuppressWarnings("RedundantIfStatement")
    boolean matches(Document d) {
        if (!decomposeConstraints().matches(d)) {
            return false;
        }
        if (since != null && d.getEnd() != null
            && (sinceExclusive ? d.getEnd().compareTo(since) <= 0 : d.getEnd().compareTo(since) < 0)) {
            return false;
        }
        if (before != null && d.getTimestamp() != null &&
            (before.equals(since) ? d.getTimestamp().compareTo(before) > 0 :
                    (beforeInclusive ? d.getTimestamp().compareTo(before) > 0 :
                            d.getTimestamp().compareTo(before) >= 0))) {
            return false;
        }
        return true;
    }
}
