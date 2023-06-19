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

import static io.fluxcapacitor.common.search.Document.Path.dotPattern;
import static io.fluxcapacitor.common.search.Document.Path.splitPattern;

@EqualsAndHashCode(callSuper = true)
@Value
@Builder(toBuilder = true)
public class SearchDocuments extends Request {
    @Default SearchQuery query = SearchQuery.builder().build();
    @Default List<String> sorting = Collections.emptyList();
    Integer maxSize;
    @Default List<String> pathFilters = Collections.emptyList();
    int skip;
    SerializedDocument lastHit;

    public Predicate<Path> computePathFilter() {
        return pathFilters.stream()
                .<Predicate<Path>>map(path -> {
                    boolean negate = path.startsWith("-");
                    path = negate ? path.substring(1) : path;
                    path = dotPattern.matcher(path).replaceAll("/");
                    Predicate<String> predicate = SearchUtils.convertGlobToRegex(path + "/**")
                            .asMatchPredicate().or(SearchUtils.convertGlobToRegex(path).asMatchPredicate());
                    var finalPredicate = negate ? predicate.negate() : predicate;
                    return splitPattern.splitAsStream(path).anyMatch(SearchUtils::isInteger)
                            ? p -> finalPredicate.test(p.getLongValue()) : p -> finalPredicate.test(p.getShortValue());
                }).reduce(Predicate::or).orElse(p -> true);
    }
}
