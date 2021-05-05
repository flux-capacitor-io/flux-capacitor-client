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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.DocumentStats;
import io.fluxcapacitor.common.api.search.SearchHistogram;
import io.fluxcapacitor.common.api.search.constraints.AnyConstraint;
import io.fluxcapacitor.common.api.search.constraints.BetweenConstraint;
import io.fluxcapacitor.common.api.search.constraints.ExistsConstraint;
import io.fluxcapacitor.common.api.search.constraints.FindConstraint;
import io.fluxcapacitor.common.api.search.constraints.MatchConstraint;
import io.fluxcapacitor.common.api.search.constraints.NotConstraint;
import io.fluxcapacitor.javaclient.FluxCapacitor;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

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
        return inPeriod(start, endExclusive, false);
    }

    Search inPeriod(Instant start, Instant endExclusive, boolean requireTimestamp);

    /*
        Other constraints
     */

    default Search find(String phrase, String... paths) {
        return constraint(FindConstraint.find(phrase, paths));
    }

    default Search match(Object constraint, String... paths) {
        return constraint(MatchConstraint.match(constraint, paths));
    }

    default Search anyExist(String... paths) {
        switch (paths.length) {
            case 0:
                return this;
            case 1:
                return constraint(new ExistsConstraint(paths[0]));
            default:
                return constraint(new AnyConstraint(Arrays.stream(paths).map(ExistsConstraint::new).collect(toList())));
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

    Search sortByTimestamp(boolean descending);

    Search sortByScore();

    Search sortBy(String path, boolean descending);

    /*
        Execution
     */

    <T> Stream<SearchHit<T>> stream();

    <T> Stream<SearchHit<T>> stream(Class<T> type);

    <T> List<T> get(int maxSize);

    <T> List<T> get(int maxSize, Class<T> type);

    SearchHistogram getHistogram(int resolution, int maxSize);

    List<DocumentStats> getStatistics(Object field, String... groupBy);

    default void delete() {
        delete(Guarantee.SENT);
    }

    void delete(Guarantee guarantee);
}
