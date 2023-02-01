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
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;
import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregateFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SelectiveCacheTest {
    private final Cache
            stringCache = spy(new DefaultCache()),
            booleanCache = spy(new DefaultCache()),
            defaultCache = spy(new DefaultCache());

    @Nested
    class CommonTests {
        private final Cache subject = new SelectiveCache(
                stringCache, v -> v instanceof String,
                new SelectiveCache(booleanCache, v -> v instanceof Boolean, defaultCache));

        @Test
        void testPut() {
            subject.put("string", "foo");
            subject.put("number", 342443);
            assertFalse(stringCache.isEmpty());
            assertTrue(booleanCache.isEmpty());
            assertFalse(defaultCache.isEmpty());
            assertFalse(subject.isEmpty());
            assertEquals(2, subject.size());
        }

        @Test
        void testPutNull() {
            subject.put("string", null);
            assertTrue(stringCache.isEmpty());
            assertTrue(booleanCache.isEmpty());
            assertFalse(defaultCache.isEmpty());
            assertFalse(subject.isEmpty());
            assertEquals(1, subject.size());
        }

        @Test
        void testPutIfAbsent() {
            subject.put("string", "foo");
            subject.putIfAbsent("number", 342443);
            subject.putIfAbsent("string", "bar");
            assertFalse(stringCache.isEmpty());
            assertTrue(booleanCache.isEmpty());
            assertFalse(defaultCache.isEmpty());
            assertFalse(subject.isEmpty());
            assertEquals(2, subject.size());
            assertEquals("foo", subject.get("string"));
            assertEquals(Integer.valueOf(342443), subject.get("number"));
        }

        @Test
        void testComputeIfAbsent() {
            subject.put("string", "foo");
            subject.computeIfAbsent("number", k -> 342443);
            subject.computeIfAbsent("string", k -> "bar");
            assertFalse(stringCache.isEmpty());
            assertTrue(booleanCache.isEmpty());
            assertFalse(defaultCache.isEmpty());
            assertFalse(subject.isEmpty());
            assertEquals(2, subject.size());
            assertEquals("foo", subject.get("string"));
            assertEquals(Integer.valueOf(342443), subject.get("number"));
        }

        @Test
        void testComputeIfPresent() {
            subject.put("string", "foo");
            subject.computeIfPresent("number", (k, v) -> 342443);
            subject.computeIfPresent("string", (k, v) -> "bar");
            assertFalse(stringCache.isEmpty());
            assertTrue(booleanCache.isEmpty());
            assertTrue(defaultCache.isEmpty());
            assertFalse(subject.isEmpty());
            assertEquals(1, subject.size());
            assertEquals("bar", subject.get("string"));
            assertNull(subject.get("number"));
        }

        @Test
        void testCompute() {
            subject.put("string", "foo");
            subject.compute("number", (k, v) -> 342443);
            subject.compute("string", (k, v) -> "bar");
            assertFalse(stringCache.isEmpty());
            assertTrue(booleanCache.isEmpty());
            assertFalse(defaultCache.isEmpty());
            assertFalse(subject.isEmpty());
            assertEquals(2, subject.size());
            assertEquals("bar", subject.get("string"));
            assertEquals(Integer.valueOf(342443), subject.get("number"));
        }

        @Test
        void testRemoveAll() {
            subject.put("string", "foo");
            subject.put("number", 342443);
            subject.put("boolean", false);
            subject.put("null", null);
            assertEquals(4, subject.size());
            subject.clear();
            assertEquals(0, subject.size());
        }

        @Test
        void testRemoveOne() {
            subject.put("string", "foo");
            subject.put("number", 342443);
            subject.put("boolean", false);
            subject.put("null", null);
            subject.remove("string");
            assertEquals(3, subject.size());
            assertNull(subject.get("string"));
        }

        @Test
        void testComputeInOtherCompute() {
            subject.compute("id1", (k, v) -> {
                subject.compute("id2", (k2, v2) -> true);
                return "foo";
            });
            assertEquals(2, subject.size());
            assertEquals(subject.get("id1"), "foo");
            assertEquals(subject.get("id2"), true);
            assertFalse(stringCache.isEmpty());
            assertFalse(booleanCache.isEmpty());
            assertTrue(defaultCache.isEmpty());
        }
    }

    @Nested
    class AggregateTests {
        private final TestFixture testFixture = TestFixture.create(
                DefaultFluxCapacitor.builder().replaceCache(defaultCache)
                        .withAggregateCache(StringModel.class, stringCache)
                        .withAggregateCache(BooleanModel.class, booleanCache),
                new MockCommandHandler());

        @Test
        void testAggregateStoredInDedicatedStringCache() {
            testFixture.whenCommand("testCommand").expectEvents("testCommand")
                    .expectFalse(fc -> stringCache.isEmpty())
                    .expectTrue(fc -> booleanCache.isEmpty())
                    .expectTrue(fc -> defaultCache.isEmpty())
                    .expectFalse(fc -> fc.cache().isEmpty());
        }

        @Test
        void testAggregateStoredInDedicatedBooleanCache() {
            testFixture.whenCommand(true).expectEvents(true)
                    .expectTrue(fc -> stringCache.isEmpty())
                    .expectFalse(fc -> booleanCache.isEmpty())
                    .expectTrue(fc -> defaultCache.isEmpty())
                    .expectFalse(fc -> fc.cache().isEmpty());
        }

        @Test
        void testAggregateStoredInDefaultCache() {
            testFixture.whenCommand(1).expectEvents(1)
                    .expectTrue(fc -> stringCache.isEmpty())
                    .expectTrue(fc -> booleanCache.isEmpty())
                    .expectFalse(fc -> defaultCache.isEmpty())
                    .expectFalse(fc -> fc.cache().isEmpty());
        }

        @Test
        void testAggregateOnlySourcedOnceAfterClear_boolean() {
            testFixture.givenCommands(true)
                    .given(fc -> fc.cache().clear())
                    .whenApplying(fc -> loadAggregateFor("test"))
                    .<Entity<?>>expectResult(e -> e.get() instanceof BooleanModel)
                    .expectFalse(fc -> booleanCache.isEmpty())
                    .expectThat(fc -> verify(fc.client().getEventStoreClient(), times(1))
                            .getEvents("test", -1L));
        }

        @Test
        void testAggregateOnlySourcedOnceAfterClear_string() {
            testFixture.givenCommands("command")
                    .given(fc -> fc.cache().clear())
                    .whenApplying(fc -> loadAggregateFor("test"))
                    .<Entity<?>>expectResult(e -> e.get() instanceof StringModel)
                    .expectFalse(fc -> stringCache.isEmpty())
                    .expectThat(fc -> verify(fc.client().getEventStoreClient(), times(1))
                            .getEvents("test", -1L));
        }

        @Test
        void testAggregateOnlySourcedOnceAfterClear_number() {
            testFixture.givenCommands(123)
                    .given(fc -> fc.cache().clear())
                    .whenApplying(fc -> loadAggregateFor("test"))
                    .<Entity<?>>expectResult(e -> e.get() instanceof NumberModel)
                    .expectFalse(fc -> defaultCache.isEmpty())
                    .expectThat(fc -> verify(fc.client().getEventStoreClient(), times(1))
                            .getEvents("test", -1L));
        }

        class MockCommandHandler {
            @HandleCommand
            void handle(String command) {
                loadAggregate("test", StringModel.class).apply(command);
            }

            @HandleCommand
            void handle(Number command) {
                loadAggregate("test", NumberModel.class).apply(command);
            }

            @HandleCommand
            void handle(Boolean command) {
                loadAggregate("test", BooleanModel.class).apply(command);
            }
        }
    }

    @Aggregate
    static class StringModel {
        @Apply
        StringModel(String event) {
        }
    }

    @Aggregate
    static class NumberModel {
        @Apply
        NumberModel(Number event) {
        }
    }

    @Aggregate
    static class BooleanModel {
        @Apply
        BooleanModel(Boolean event) {
        }
    }


}