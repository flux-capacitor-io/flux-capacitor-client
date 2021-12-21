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

package io.fluxcapacitor.javaclient.persisting.search;

import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.DocumentStats;
import io.fluxcapacitor.common.api.search.SearchHistogram;
import io.fluxcapacitor.common.api.search.constraints.AnyConstraint;
import io.fluxcapacitor.common.api.search.constraints.BetweenConstraint;
import io.fluxcapacitor.common.api.search.constraints.ExistsConstraint;
import io.fluxcapacitor.common.api.search.constraints.LookAheadConstraint;
import io.fluxcapacitor.common.api.search.constraints.MatchConstraint;
import io.fluxcapacitor.common.api.search.constraints.NotConstraint;
import io.fluxcapacitor.common.api.search.constraints.QueryConstraint;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import lombok.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public interface Search {

    /*
        Timing
     */

    default Search since(Instant start) {
        return inPeriod(start, null);
    }

    default Search before(Instant endExclusive) {
        return inPeriod(null, endExclusive);
    }

    default Search beforeLast(Duration period) {
        return before(FluxCapacitor.currentClock().instant().minus(period));
    }

    default Search inLast(Duration period) {
        return since(FluxCapacitor.currentClock().instant().minus(period));
    }

    default Search inPeriod(Instant start, Instant endExclusive) {
        return inPeriod(start, endExclusive, true);
    }

    Search inPeriod(Instant start, Instant endExclusive, boolean requireTimestamp);

    /*
        Other constraints
     */

    default Search lookAhead(String phrase, String... paths) {
        return constraint(LookAheadConstraint.lookAhead(phrase, paths));
    }

    default Search query(String phrase, String... paths) {
        return constraint(QueryConstraint.query(phrase, paths));
    }

    default Search match(Object constraint, String... paths) {
        return match(constraint, false, paths);
    }

    default Search match(Object constraint, boolean strict, String... paths) {
        return constraint(MatchConstraint.match(constraint, strict, paths));
    }

    default Search anyExist(String... paths) {
        switch (paths.length) {
            case 0:
                return this;
            case 1:
                return constraint(ExistsConstraint.exists(paths[0]));
            default:
                return constraint(AnyConstraint.any(Arrays.stream(paths).map(ExistsConstraint::exists).collect(toList())));
        }
    }

    default Search atLeast(Number min, String path) {
        return between(min, null, path);
    }

    default Search below(Number max, String path) {
        return between(null, max, path);
    }

    default Search between(Number min, Number maxExclusive, String path) {
        return constraint(BetweenConstraint.between(min, maxExclusive, path));
    }

    default Search any(Constraint... constraints) {
        return constraint(AnyConstraint.any(constraints));
    }

    default Search not(Constraint constraint) {
        return constraint(NotConstraint.not(constraint));
    }

    Search constraint(Constraint... constraints);

    /*
        Sorting
     */

    default Search sortByTimestamp() {
        return sortByTimestamp(false);
    }

    Search sortByTimestamp(boolean descending);

    Search sortByScore();

    default Search sortBy(String path) {
        return sortBy(path, false);
    }

    Search sortBy(String path, boolean descending);

    /*
        Content filtering
     */

    Search exclude(String... paths);

    Search includeOnly(String... paths);

    Search skip(Integer n);

    /*
        Execution
     */

    <T> List<T> fetch(int maxSize);

    <T> List<T> fetch(int maxSize, Class<T> type);

    default <T> List<T> fetchAll() {
        return this.<T>stream().collect(toList());
    }

    default <T> List<T> fetchAll(Class<T> type) {
        return this.stream(type).collect(toList());
    }

    default <T> Stream<T> stream() {
        return this.<T>streamHits().map(SearchHit::getValue);
    }

    default <T> Stream<T> stream(Class<T> type) {
        return this.streamHits(type).map(SearchHit::getValue);
    }

    default <T> Optional<T> fetchFirst() {
        return this.<T>fetch(1).stream().findFirst();
    }

    default <T> Optional<T> fetchFirst(Class<T> type) {
        return this.fetch(1, type).stream().findFirst();
    }

    <T> Stream<SearchHit<T>> streamHits();

    <T> Stream<SearchHit<T>> streamHits(Class<T> type);

    SearchHistogram fetchHistogram(int resolution, int maxSize);

    default List<DocumentStats> fetchStatistics(@NonNull String field, String... groupBy) {
        return fetchStatistics(List.of(field), groupBy);
    }

    List<DocumentStats> fetchStatistics(List<String> fields, String... groupBy);

    default Map<Map<String, String>, Long> getDocumentStatistics(String... groupBy) {
        return fetchStatistics(List.of(""), groupBy).stream().collect(toMap(DocumentStats::getGroup, s ->
                s.getFieldStats().values().stream().map(DocumentStats.FieldStats::getCount).findFirst().orElse(0L)));
    }

    CompletableFuture<Void> delete();
}
