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

package io.fluxcapacitor.javaclient.modeling;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.With;

/**
 * Represents configuration options extracted from the
 * {@link io.fluxcapacitor.javaclient.persisting.search.Searchable @Searchable} annotation on a domain type. These
 * parameters determine how the entity should be indexed and queried within the {@code DocumentStore} and related search
 * infrastructure.
 *
 * <p>Instances of this class are typically created during reflective analysis of annotated classes and cached
 * for repeated lookups. They encapsulate metadata relevant to how documents are indexed, timestamped, and grouped
 * within collections.
 *
 * <h2>Fields:</h2>
 * <ul>
 *   <li>{@code searchable} – whether documents of this type should be indexed and searchable.</li>
 *   <li>{@code collection} – name of the logical collection under which documents are grouped; if not
 *       specified, the simple name of the class is used.</li>
 *   <li>{@code timestampPath} – expression to extract the primary timestamp (start time) from the object.</li>
 *   <li>{@code endPath} – expression to extract the end timestamp (for ranged entities) from the object.</li>
 * </ul>
 *
 * @see io.fluxcapacitor.javaclient.persisting.search.Searchable
 * @see io.fluxcapacitor.javaclient.persisting.search.DocumentStore
 */
@Value
@AllArgsConstructor
@Builder
public class SearchParameters {
    /**
     * Default instance with {@code searchable=true}, and no collection/timestamp configuration.
     */
    public static final SearchParameters defaultSearchParameters = SearchParameters.builder().build();

    /**
     * Whether instances of the annotated class are eligible for indexing and search. Defaults to {@code true}.
     */
    @Builder.Default
    boolean searchable = true;

    /**
     * Optional name of the document collection to use. If not specified, the simple class name will be used as the
     * default.
     */
    @With
    String collection;

    /**
     * Optional property path expression that points to the document's start timestamp (e.g. {@code "createdAt"}). Used
     * to associate temporal metadata with the document.
     */
    String timestampPath;

    /**
     * Optional property path expression that points to the document's end timestamp (e.g. {@code "expiresAt"}). If
     * unspecified, the {@code timestampPath} is used as both start and end.
     */
    String endPath;
}
