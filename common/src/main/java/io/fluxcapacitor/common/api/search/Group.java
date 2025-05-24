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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a group key for aggregating documents in {@link DocumentStats}.
 * <p>
 * A {@code Group} consists of one or more path-value pairs used to categorize documents
 * during aggregation. Each group corresponds to a unique combination of values across
 * the specified {@code groupBy} paths.
 * <p>
 * Groups are typically created during the in-memory computation of statistics in a
 * {@link io.fluxcapacitor.common.api.search.GetDocumentStatsResult}, and identify which
 * subset of documents a given {@link DocumentStats} applies to.
 *
 * <p><strong>Example:</strong><br>
 * If grouping on {@code "status"} and {@code "region"}, one {@code Group} might contain:<br>
 * {@code {"status": "active", "region": "EU"}}
 */
@Value
public class Group {

    /**
     * Creates a {@code Group} from an even-length list of alternating keys and values.
     *
     * @param pathsAndValues An array of alternating path names and their corresponding values
     * @return A new {@code Group} instance
     * @throws IllegalArgumentException if the number of elements is odd
     */
    public static Group of(String... pathsAndValues) {
        if (pathsAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Number of values should be even");
        }
        LinkedHashMap<String, String> map = new LinkedHashMap<>(pathsAndValues.length/2);
        for (int i = 0; i < pathsAndValues.length; i += 2) {
            map.put(pathsAndValues[i], pathsAndValues[i+1]);
        }
        return new Group(map);
    }

    /**
     * The mapping of path names to their grouped values.
     */
    @JsonValue
    Map<String, String> values;

    @JsonCreator
    public Group(Map<String, String> values) {
        this.values = values;
    }

    /**
     * Returns the value associated with the given path in this group.
     *
     * @param path the path name
     * @return the grouped value, or {@code null} if not present
     */
    public String get(String path) {
        return values.get(path);
    }
}
