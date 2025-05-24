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

import io.fluxcapacitor.common.SearchUtils;
import io.fluxcapacitor.common.api.Request;
import io.fluxcapacitor.common.search.Document.Path;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.SearchUtils.normalizePath;
import static io.fluxcapacitor.common.search.Document.Path.isLongPath;

/**
 * Request used to perform a search over documents indexed in the Flux search store.
 * <p>
 * This class is sent to the Flux platform and supports a rich query mechanism, pagination, sorting, and selective field
 * filtering.
 *
 * <p><strong>Query:</strong> The {@link #query} field specifies the main search criteria (document type, text query,
 * constraints, etc.).
 *
 * <p><strong>Sorting:</strong> Use {@link #sorting} to specify the fields used for ordering results. Each entry should
 * refer to a field name, optionally prefixed with {@code -} to indicate descending order (e.g., {@code -timestamp}).
 *
 * <p><strong>Pagination:</strong> Use {@link #skip} to offset the result window. The {@link #maxSize} limits the
 * number of results returned. If {@link #lastHit} is provided, the search will continue from the given last result
 * (useful for deep pagination).
 *
 * <p><strong>Path filtering:</strong> The {@link #pathFilters} field lets you restrict which fields are included in
 * each search hit, using glob-like syntax (e.g., {@code details/name}, {@code -private/**} to exclude sensitive paths).
 * Filtering is applied using {@link #computePathFilter()}.
 */
@EqualsAndHashCode(callSuper = true)
@Value
@Builder(toBuilder = true)
public class SearchDocuments extends Request {
    @Default
    SearchQuery query = SearchQuery.builder().build();
    @Default
    List<String> sorting = Collections.emptyList();
    Integer maxSize;
    @Default
    List<String> pathFilters = Collections.emptyList();
    int skip;
    SerializedDocument lastHit;

    /**
     * Computes a path-level filter based on the {@link #pathFilters} list.
     * <p>
     * Each entry in the list can be a glob pattern for inclusion (e.g., {@code details/name}) or exclusion
     * (e.g., {@code -details/private/**}).
     * <p>
     * Patterns are matched against both the short and long form of a {@link Path},
     * depending on the structure of the pattern.
     *
     * @return a {@link Predicate} that returns {@code true} for paths that should be included in search hits.
     */
    public Predicate<Path> computePathFilter() {
        Predicate<Path> excludeFilter = pathFilters.stream().filter(p -> p.startsWith("-"))
                .<Predicate<Path>>map(path -> {
                    path = normalizePath(path.substring(1));
                    Predicate<String> predicate = SearchUtils.getGlobMatcher(path + "/**")
                            .or(SearchUtils.getGlobMatcher(path)).negate();
                    return isLongPath(path)
                            ? p -> predicate.test(p.getLongValue()) : p -> predicate.test(p.getShortValue());
                }).reduce(Predicate::and).orElse(p -> true);
        Predicate<Path> includeFilter = pathFilters.stream().filter(p -> !p.startsWith("-"))
                .<Predicate<Path>>map(path -> {
                    path = normalizePath(path);
                    Predicate<String> predicate = SearchUtils.getGlobMatcher(path + "/**")
                            .or(SearchUtils.getGlobMatcher(path));
                    return isLongPath(path)
                            ? p -> predicate.test(p.getLongValue()) : p -> predicate.test(p.getShortValue());
                }).reduce(Predicate::or).orElse(p -> true);
        return includeFilter.and(excludeFilter);
    }
}
