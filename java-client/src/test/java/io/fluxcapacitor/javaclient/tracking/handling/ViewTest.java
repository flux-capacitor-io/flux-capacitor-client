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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.search.SearchExclude;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.modeling.Id;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

public class ViewTest {

    @Nested
    class StaticTests {
        private final TestFixture testFixture = TestFixture.create(StaticView.class);

        @Test
        void viewIsCreated() {
            testFixture.whenEvent(new SomeEvent("foo")).expectCommands(1);
        }

        @Test
        void viewIsUpdated() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new SomeEvent("foo"))
                    .expectOnlyCommands(2);
        }

        @Test
        void viewIsNotUpdatedIfNoMatch() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new SomeEvent("other"))
                    .expectOnlyCommands(1);
        }

        @Test
        void viewIsUpdated_alias() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new AliasEvent(new AliasId("foo")))
                    .expectOnlyCommands(2);
        }

        @Test
        void viewIsNotUpdated_wrongAlias() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new AliasEvent(new AliasId("other")))
                    .expectNoCommands();
        }

        @Test
        void viewIsUpdated_associationOnMethod() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new CustomEvent("foo"))
                    .expectOnlyCommands(2);
        }

        @View
        @SearchExclude
        @Value
        @Builder(toBuilder = true)
        public static class StaticView {
            @Association({"someId", "aliasId"}) String someId;
            int eventCount;

            @HandleEvent
            static StaticView create(SomeEvent event) {
                FluxCapacitor.sendAndForgetCommand(1);
                return StaticView.builder().someId(event.someId).eventCount(1).build();
            }

            @HandleEvent
            StaticView update(SomeEvent event) {
                FluxCapacitor.sendAndForgetCommand(eventCount + 1);
                return toBuilder().eventCount(eventCount + 1).build();
            }

            @HandleEvent
            StaticView update(AliasEvent event) {
                FluxCapacitor.sendAndForgetCommand(eventCount + 1);
                return toBuilder().eventCount(eventCount + 1).build();
            }

            @HandleEvent
            @Association("customId")
            StaticView update(CustomEvent event) {
                FluxCapacitor.sendAndForgetCommand(eventCount + 1);
                return toBuilder().eventCount(eventCount + 1).build();
            }
        }
    }

    @Nested
    class ConstructorTests {
        private final TestFixture testFixture = TestFixture.create(ConstructorView.class);

        @Test
        void viewIsCreated() {
            testFixture.whenEvent(new SomeEvent("foo"))
                    .expectCommands(1)
                    .expectTrue(fc -> fc.documentStore().fetchDocument("foo", ConstructorView.class).isPresent());
        }

        @Test
        void viewIsUpdated() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new SomeEvent("foo"))
                    .expectCommands(2);
        }

        @Test
        void viewIsUpdated_async() {
            TestFixture.createAsync(ConstructorView.class).givenEvents(new SomeEvent("foo"))
                    .whenEvent(new SomeEvent("foo"))
                    .expectCommands(2);
        }

        @View
        @Value
        @Builder(toBuilder = true)
        @AllArgsConstructor
        public static class ConstructorView {
            @EntityId
            @Association String someId;
            int eventCount;

            @HandleEvent
            ConstructorView(SomeEvent event) {
                this(event.getSomeId(), 1);
                FluxCapacitor.sendAndForgetCommand(eventCount);
            }

            @HandleEvent
            ConstructorView update(SomeEvent event) {
                FluxCapacitor.sendAndForgetCommand(eventCount + 1);
                return toBuilder().eventCount(eventCount + 1).build();
            }
        }
    }

    @Nested
    class CustomAssociationProperty {
        private final TestFixture testFixture = TestFixture.create(SomeView.class);

        @Test
        void viewIsCreated() {
            testFixture.whenEvent(new SomeEvent("foo")).expectCommands(1);
        }

        @Test
        void viewIsUpdated() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new SomeEvent("foo"))
                    .expectCommands(2);
        }

        @View(timestampPath = "timestamp")
        @Value
        @Builder(toBuilder = true)
        @AllArgsConstructor
        public static class SomeView {
            @Association("someId") String id;
            int eventCount;
            Instant timestamp = FluxCapacitor.currentTime();

            @HandleEvent
            SomeView(SomeEvent event) {
                this(event.getSomeId(), 1);
                FluxCapacitor.sendAndForgetCommand(eventCount);
            }

            @HandleEvent
            SomeView update(SomeEvent event) {
                FluxCapacitor.sendAndForgetCommand(eventCount + 1);
                return toBuilder().eventCount(eventCount + 1).build();
            }
        }
    }

    @Nested
    class MappingTests {
        @View
        @Value
        static class MappingView {
            String someId;

            @HandleEvent
            static MappingView handle(String event) {
                return new MappingView(event);
            }
        }

        @Test
        void mappingTest() {
            TestFixture.create(MappingView.class).whenEvent("foo")
                    .expectTrue(fc -> FluxCapacitor.search(MappingView.class).fetchAll().size() == 1);
        }
    }

    @Value
    static class SomeEvent {
        String someId;
    }

    @Value
    static class AliasEvent {
        AliasId aliasId;
    }

    static class AliasId extends Id<Object> {
        protected AliasId(String id) {
            super(id, Object.class, "alias-");
        }
    }

    @Value
    static class CustomEvent {
        String customId;
    }
}
