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
