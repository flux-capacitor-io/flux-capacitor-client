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

import io.fluxcapacitor.javaclient.persisting.eventsourcing.Aggregate;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;
import static io.fluxcapacitor.javaclient.FluxCapacitor.search;

public class SearchableAggregateTest {

    private final TestFixture testFixture = TestFixture.create();

    @Test
    void testAggregateIsSearchableAfterApply() {
        testFixture.when(fc -> loadAggregate("123", SearchableAggregate.class, false)
                        .update(a -> new SearchableAggregate("bar")))
                .expectDocuments(List.of(new SearchableAggregate("bar")))
                .expectFalse(fc -> search(SearchableAggregate.class.getSimpleName()).getAll().isEmpty())
                .expectTrue(fc -> search("searchables").getAll().isEmpty());
    }

    @Test
    void testAggregateIsDeletedFromDocumentStoreAutomatically() {
        testFixture.given(fc -> loadAggregate("123", SearchableAggregate.class, false)
                        .update(a -> new SearchableAggregate("bar")))
                .when(fc -> loadAggregate("123", SearchableAggregate.class, false).update(a -> null))
                .expectTrue(fc -> search(SearchableAggregate.class.getSimpleName()).getAll().isEmpty());
    }

    @Test
    void testAggregateWithTimePath() {
        Instant timestamp = Instant.now().minusSeconds(1000);
        testFixture.when(fc -> loadAggregate("123", SearchableAggregateWithTimePath.class, false)
                        .update(a -> new SearchableAggregateWithTimePath(timestamp)))
                .expectDocuments(List.of(new SearchableAggregateWithTimePath(timestamp)))
                .expectFalse(fc -> search(SearchableAggregateWithTimePath.class.getSimpleName())
                        .before(timestamp.plusSeconds(1)).getAll().isEmpty())
                .expectTrue(fc -> search(SearchableAggregateWithTimePath.class.getSimpleName())
                        .since(timestamp.plusSeconds(1)).getAll().isEmpty());
    }

    @Test
    void testAggregateWithTimePathPropertyMissing() {
        Instant timestamp = Instant.now().minusSeconds(1000);
        testFixture.when(fc -> loadAggregate("123", SearchableAggregateWithMissingTimePath.class, false)
                        .update(a -> new SearchableAggregateWithMissingTimePath(timestamp)))
                .expectTrue(fc -> search(SearchableAggregateWithMissingTimePath.class.getSimpleName())
                        .before(timestamp.plusSeconds(1)).getAll().isEmpty())
                .expectFalse(fc -> search(SearchableAggregateWithMissingTimePath.class.getSimpleName())
                        .since(timestamp.plusSeconds(1)).getAll().isEmpty());
    }

    @Test
    void testAggregateWithCustomCollection() {
        testFixture.when(fc -> loadAggregate("123", SearchableAggregateWithCustomCollection.class, false)
                        .update(a -> new SearchableAggregateWithCustomCollection("bar")))
                .expectDocuments(List.of(new SearchableAggregateWithCustomCollection("bar")))
                .expectFalse(fc -> search("searchables").getAll().isEmpty());
    }

    @Aggregate(eventSourced = false, searchable = true, cached = false)
    @Value
    static class SearchableAggregate {
        String foo;
    }

    @Aggregate(eventSourced = false, searchable = true, collection = "searchables")
    @Value
    static class SearchableAggregateWithCustomCollection {
        String foo;
    }

    @Aggregate(eventSourced = false, searchable = true, timestampPath = "timestamp")
    @Value
    static class SearchableAggregateWithTimePath {
        Instant timestamp;
    }

    @Aggregate(eventSourced = false, searchable = true, timestampPath = "timestamp")
    @Value
    static class SearchableAggregateWithMissingTimePath {
        Instant time;
    }
}
