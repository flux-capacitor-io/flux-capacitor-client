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

public class StatefulHandlerTest {

    @Nested
    class StaticTests {
        private final TestFixture testFixture = TestFixture.create(StaticHandler.class);

        @Test
        void handlerIsCreated() {
            testFixture.whenEvent(new SomeEvent("foo")).expectCommands(1);
        }

        @Test
        void handlerIsUpdated() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new SomeEvent("foo"))
                    .expectOnlyCommands(2);
        }

        @Test
        void handlerIsNotUpdatedIfNoMatch() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new SomeEvent("other"))
                    .expectOnlyCommands(1);
        }

        @Test
        void handlerIsUpdated_alias() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new AliasEvent(new AliasId("foo")))
                    .expectOnlyCommands(2);
        }

        @Test
        void handlerIsNotUpdated_wrongAlias() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new AliasEvent(new AliasId("other")))
                    .expectNoCommands();
        }

        @Test
        void handlerIsUpdated_associationOnMethod() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new CustomEvent("foo"))
                    .expectOnlyCommands(2);
        }

        @Stateful
        @SearchExclude
        @Value
        @Builder(toBuilder = true)
        public static class StaticHandler {
            @Association({"someId", "aliasId"}) String someId;
            int eventCount;

            @HandleEvent
            static StaticHandler create(SomeEvent event) {
                FluxCapacitor.sendAndForgetCommand(1);
                return StaticHandler.builder().someId(event.someId).eventCount(1).build();
            }

            @HandleEvent
            StaticHandler update(SomeEvent event) {
                FluxCapacitor.sendAndForgetCommand(eventCount + 1);
                return toBuilder().eventCount(eventCount + 1).build();
            }

            @HandleEvent
            StaticHandler update(AliasEvent event) {
                FluxCapacitor.sendAndForgetCommand(eventCount + 1);
                return toBuilder().eventCount(eventCount + 1).build();
            }

            @HandleEvent
            @Association("customId")
            StaticHandler update(CustomEvent event) {
                FluxCapacitor.sendAndForgetCommand(eventCount + 1);
                return toBuilder().eventCount(eventCount + 1).build();
            }
        }
    }

    @Nested
    class ConstructorTests {
        private final TestFixture testFixture = TestFixture.create(ConstructorHandler.class);

        @Test
        void handlerIsCreated() {
            testFixture.whenEvent(new SomeEvent("foo"))
                    .expectCommands(1)
                    .expectTrue(fc -> fc.documentStore().fetchDocument("foo", ConstructorHandler.class).isPresent());
        }

        @Test
        void handlerIsUpdated() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new SomeEvent("foo"))
                    .expectCommands(2);
        }

        @Test
        void handlerIsUpdated_async() {
            TestFixture.createAsync(ConstructorHandler.class).givenEvents(new SomeEvent("foo"))
                    .whenEvent(new SomeEvent("foo"))
                    .expectCommands(2);
        }

        @Stateful
        @Value
        @Builder(toBuilder = true)
        @AllArgsConstructor
        public static class ConstructorHandler {
            @EntityId
            @Association String someId;
            int eventCount;

            @HandleEvent
            ConstructorHandler(SomeEvent event) {
                this(event.getSomeId(), 1);
                FluxCapacitor.sendAndForgetCommand(eventCount);
            }

            @HandleEvent
            ConstructorHandler update(SomeEvent event) {
                FluxCapacitor.sendAndForgetCommand(eventCount + 1);
                return toBuilder().eventCount(eventCount + 1).build();
            }
        }
    }

    @Nested
    class CustomAssociationProperty {
        private final TestFixture testFixture = TestFixture.create(SomeHandler.class);

        @Test
        void handlerIsCreated() {
            testFixture.whenEvent(new SomeEvent("foo")).expectCommands(1);
        }

        @Test
        void handlerIsUpdated() {
            testFixture.givenEvents(new SomeEvent("foo"))
                    .whenEvent(new SomeEvent("foo"))
                    .expectCommands(2);
        }

        @Stateful(timestampPath = "timestamp")
        @Value
        @Builder(toBuilder = true)
        @AllArgsConstructor
        public static class SomeHandler {
            @Association("someId") String id;
            int eventCount;
            Instant timestamp = FluxCapacitor.currentTime();

            @HandleEvent
            SomeHandler(SomeEvent event) {
                this(event.getSomeId(), 1);
                FluxCapacitor.sendAndForgetCommand(eventCount);
            }

            @HandleEvent
            SomeHandler update(SomeEvent event) {
                FluxCapacitor.sendAndForgetCommand(eventCount + 1);
                return toBuilder().eventCount(eventCount + 1).build();
            }
        }
    }

    @Nested
    class MappingTests {
        @Stateful
        @Value
        static class MappingHandler {
            String someId;

            @HandleEvent
            static MappingHandler handle(String event) {
                return new MappingHandler(event);
            }
        }

        @Test
        void mappingTest() {
            TestFixture.create(MappingHandler.class).whenEvent("foo")
                    .expectTrue(fc -> FluxCapacitor.search(MappingHandler.class).fetchAll().size() == 1);
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
