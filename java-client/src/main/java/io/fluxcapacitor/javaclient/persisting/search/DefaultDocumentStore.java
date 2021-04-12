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
import io.fluxcapacitor.common.api.search.SearchQuery;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;

@AllArgsConstructor
public class DefaultDocumentStore implements DocumentStore {
    private final SearchClient client;
    @Getter
    private final DocumentSerializer serializer;

    @Override
    public void index(Object object, String id, Instant timestamp, String collection, Guarantee guarantee) {
        try {
            client.index(singletonList(serializer.toDocument(object, id, collection, timestamp)), guarantee).await();
        } catch (Exception e) {
            throw new DocumentStoreException(String.format("Could not store a document %s for id %s", object, id), e);
        }
    }

    @Override
    public Search search(SearchQuery.Builder searchBuilder) {
        return new DefaultSearch(searchBuilder);
    }

    @AllArgsConstructor
    private class DefaultSearch implements Search {
        private final SearchQuery.Builder queryBuilder;
        private final List<String> sorting = new ArrayList<>();

        protected DefaultSearch() {
            this(SearchQuery.builder());
        }

        @Override
        public Search inPeriod(Instant start, Instant endExclusive) {
            if (start != null) {
                queryBuilder.since(start);
            }
            if (endExclusive != null) {
                queryBuilder.before(endExclusive);
            }
            return this;
        }

        @Override
        public Search constraint(Constraint... constraints) {
            switch (constraints.length) {
                case 0:
                    break;
                case 1:
                    queryBuilder.constraint(constraints[0]);
                    break;
                default:
                    queryBuilder.constraints(Arrays.asList(constraints));
                    break;
            }
            return this;
        }

        @Override
        public Search sortByTimestamp(boolean descending) {
            sorting.add(descending ? "-" : "" + "_timestamp");
            return this;
        }

        @Override
        public Search sortByScore() {
            sorting.add("-_score");
            return this;
        }

        @Override
        public Search sortBy(String path, boolean descending) {
            sorting.add(descending ? "-" : "" + path);
            return this;
        }

        @Override
        public <T> Stream<SearchHit<T>> stream() {
            return client.search(queryBuilder.build(), sorting).map(hit -> hit.map(serializer::fromDocument));
        }

        @Override
        public <T> Stream<SearchHit<T>> stream(Class<T> type) {
            return client.search(queryBuilder.build(), sorting).map(hit -> hit.map(document -> serializer
                    .fromDocument(document, type)));
        }

        @Override
        public SearchHistogram getHistogram(int resolution) {
            return client.getHistogram(queryBuilder.build(), resolution);
        }

        @Override
        public List<DocumentStats> getStatistics(@NonNull Object field, String... groupBy) {
            List<String> fields;
            if (field instanceof String) {
                fields = singletonList((String) field);
            } else if (field instanceof Collection<?>) {
                fields = ((Collection<?>) field).stream().map(Objects::toString).collect(Collectors.toList());
            } else {
                throw new IllegalArgumentException("Failed to parse field. Expected Collection or String. Got: " + field);
            }
            return client.getStatistics(queryBuilder.build(), fields, Arrays.<String>asList(groupBy));
        }

        @Override
        public void delete(Guarantee guarantee) {
            client.delete(queryBuilder.build(), guarantee);
        }
    }
}
