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

package io.fluxcapacitor.javaclient.persisting.search;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;
import static io.fluxcapacitor.javaclient.FluxCapacitor.search;

public class SearchableAggregateTest {

    private final TestFixture testFixture = TestFixture.create(new QueryHandler());

    @Test
    void testDefaultAggregateIsNotSearchableAfterApply() {
        testFixture.whenExecuting(fc -> loadAggregate("123", NotSearchableAggregate.class)
                        .update(a -> new NotSearchableAggregate("bar")))
                .expectTrue(fc -> fc.documentStore().search("NotSearchableAggregate").fetchAll().isEmpty())
                .expectTrue(fc -> search(NotSearchableAggregate.class.getSimpleName()).fetchAll().isEmpty())
                .expectTrue(fc -> search("searchables").fetchAll().isEmpty());
    }

    @Test
    void testAggregateIsSearchableAfterApply() {
        testFixture.whenExecuting(fc -> loadAggregate("123", SearchableAggregate.class)
                        .update(a -> new SearchableAggregate("bar")))
                .expectTrue(fc -> fc.documentStore().search("SearchableAggregate").fetchAll().equals(List.of(new SearchableAggregate("bar"))))
                .expectFalse(fc -> search(SearchableAggregate.class.getSimpleName()).fetchAll().isEmpty())
                .expectTrue(fc -> search("searchables").fetchAll().isEmpty());
    }

    @Test
    void testAggregateIsDeletedFromDocumentStoreAutomatically() {
        testFixture.given(fc -> loadAggregate("123", SearchableAggregate.class)
                        .update(a -> new SearchableAggregate("bar")))
                .whenExecuting(fc -> loadAggregate("123", SearchableAggregate.class).update(a -> null))
                .expectTrue(fc -> search(SearchableAggregate.class.getSimpleName()).fetchAll().isEmpty());
    }

    @Test
    void testAggregateWithTimePath() {
        Instant timestamp = Instant.now().minusSeconds(1000);
        Instant ending = timestamp.plusSeconds(60);
        testFixture.whenExecuting(fc -> loadAggregate("123", SearchableAggregateWithTimePath.class)
                        .update(a -> new SearchableAggregateWithTimePath(timestamp, ending)))
                .expectTrue(fc -> fc.documentStore().search("SearchableAggregateWithTimePath").fetchAll()
                        .equals(List.of(new SearchableAggregateWithTimePath(timestamp, ending))))
                .expectFalse(fc -> search(SearchableAggregateWithTimePath.class.getSimpleName())
                        .before(timestamp.plusSeconds(1)).fetchAll().isEmpty())
                .expectFalse(fc -> search(SearchableAggregateWithTimePath.class.getSimpleName())
                        .since(timestamp.plusSeconds(1)).fetchAll().isEmpty())
                .expectTrue(fc -> search(SearchableAggregateWithTimePath.class.getSimpleName())
                        .since(timestamp.plusSeconds(61)).fetchAll().isEmpty());
    }

    @Test
    void testAggregateWithTimePathPropertyMissing() {
        Instant timestamp = Instant.now().minusSeconds(1000);
        testFixture.whenExecuting(fc -> loadAggregate("123", SearchableAggregateWithMissingTimePath.class)
                        .update(a -> new SearchableAggregateWithMissingTimePath(timestamp)))
                .expectTrue(fc -> search(SearchableAggregateWithMissingTimePath.class.getSimpleName())
                        .before(timestamp.plusSeconds(1)).fetchAll().isEmpty())
                .expectFalse(fc -> search(SearchableAggregateWithMissingTimePath.class.getSimpleName())
                        .since(timestamp.plusSeconds(1)).fetchAll().isEmpty());
    }

    @Test
    void testAggregateWithCustomCollection() {
        testFixture.whenExecuting(fc -> loadAggregate("123", SearchableAggregateWithCustomCollection.class)
                        .update(a -> new SearchableAggregateWithCustomCollection("bar")))
                .expectTrue(fc -> fc.documentStore().search(SearchableAggregateWithCustomCollection.class).fetchAll().equals(List.of(new SearchableAggregateWithCustomCollection("bar"))))
                .expectFalse(fc -> search(SearchableAggregateWithCustomCollection.class).fetchAll().isEmpty());
    }

    @Test
    void testAggregateWithCustomCollectionViaQuery() {
        testFixture.given(fc -> loadAggregate("123", SearchableAggregateWithCustomCollection.class)
                        .update(a -> new SearchableAggregateWithCustomCollection("bar")))
                .whenQuery(new GetAggregates("searchables"))
                .expectResultContaining(new SearchableAggregateWithCustomCollection("bar"));
    }

    record GetAggregates(String collection) {
    }

    static class QueryHandler {
        @HandleQuery
        List<?> handle(GetAggregates query) {
            return FluxCapacitor.search(query.collection()).fetchAll();
        }
    }

    @Aggregate(cached = false)
    record NotSearchableAggregate(String foo) {
    }

    @Aggregate(eventSourced = false, searchable = true, cached = false)
    record SearchableAggregate(String foo) {
    }

    @Aggregate(eventSourced = false, searchable = true, collection = "searchables")
    record SearchableAggregateWithCustomCollection(String foo) {
    }

    @Aggregate(eventSourced = false, searchable = true, timestampPath = "timestamp", endPath = "ending")
    record SearchableAggregateWithTimePath(Instant timestamp, Instant ending) {
    }

    @Aggregate(eventSourced = false, searchable = true, timestampPath = "timestamp")
    record SearchableAggregateWithMissingTimePath(Instant time) {
    }
}
