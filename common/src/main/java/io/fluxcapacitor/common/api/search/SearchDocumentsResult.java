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

import io.fluxcapacitor.common.api.QueryResult;
import lombok.Value;

import java.util.List;

@Value
public class SearchDocumentsResult implements QueryResult {
    long requestId;
    List<SerializedDocument> matches;
    long timestamp = System.currentTimeMillis();

    @Override
    public Metric toMetric() {
        return new Metric(matches.size(), timestamp);
    }

    public int size() {
        return matches.size();
    }

    public SerializedDocument lastMatch() {
        return matches.isEmpty() ? null : matches.get(matches.size() - 1);
    }

    @Value
    public static class Metric {
        int size;
        long timestamp;
    }
}