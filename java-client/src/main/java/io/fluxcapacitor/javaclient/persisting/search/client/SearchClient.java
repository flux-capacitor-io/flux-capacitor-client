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

package io.fluxcapacitor.javaclient.persisting.search.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.search.DocumentStats;
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.javaclient.persisting.search.SearchHit;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public interface SearchClient extends AutoCloseable {

    default Awaitable index(Document... documents) {
        return index(Arrays.asList(documents), Guarantee.SENT);
    }

    Awaitable index(List<Document> documents, Guarantee guarantee);

    Stream<SearchHit<Document>> search(SearchQuery query, List<String> sorting);

    Awaitable delete(SearchQuery query, Guarantee guarantee);

    List<DocumentStats> getStatistics(SearchQuery query, List<String> fields, List<String> groupBy);

    @Override
    void close();
}
