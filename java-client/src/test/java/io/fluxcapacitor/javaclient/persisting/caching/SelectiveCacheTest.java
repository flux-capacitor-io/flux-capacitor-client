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

package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.ApplyEvent;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;
import static org.mockito.Mockito.spy;

class SelectiveCacheTest {
    private final Cache defaultCache = spy(new DefaultCache()), customCache = spy(new DefaultCache());

    private final TestFixture testFixture = TestFixture.create(
            DefaultFluxCapacitor.builder().replaceCache(defaultCache).withAggregateCache(MockModel.class, customCache),
            new MockCommandHandler());

    @Test
    void testAggregateStoredInDedicatedCache() {
        testFixture.whenCommand("testCommand").expectEvents("testCommand")
                .expectTrue(fc -> defaultCache.isEmpty())
                .expectFalse(fc -> customCache.isEmpty());
    }

    @Test
    void testOtherAggregateStoredInDefaultCache() {
        testFixture.whenCommand(1).expectEvents(1)
                .expectTrue(fc -> customCache.isEmpty())
                .expectFalse(fc -> defaultCache.isEmpty());
    }

    static class MockCommandHandler {
        @HandleCommand
        void handle(String command) {
            loadAggregate("test", MockModel.class).apply(command);
        }

        @HandleCommand
        void handle(Integer command) {
            loadAggregate("test", OtherModel.class).apply(command);
        }
    }

    @Aggregate
    static class MockModel {
        @ApplyEvent
        MockModel(String event) {
        }
    }

    @Aggregate
    static class OtherModel {
        @ApplyEvent
        OtherModel(Integer event) {
        }
    }

}