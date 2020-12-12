/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourced;
import io.fluxcapacitor.javaclient.test.AbstractTestFixture;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.test.streaming.StreamingTestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.Value;
import lombok.With;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.givenwhenthen.GivenWhenThenTestUtils.getRepo;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GivenWhenThenEntityIdTest {
    private static final String parentId = "parent", childId = "child";

    public static class WithEntityIdInChild {

        private final CreateParent createParent = new CreateParent(parentId);
        private final CreateChild createChild = new CreateChild(childId);

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testCreateChild(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent).whenCommand(createChild).expectOnlyEvents(createChild)
                    .verify(() -> assertEquals(new Child(childId, 0),
                            getRepo(testFixture).load(parentId, Parent.class).get().getChild()));
        }

        private static Stream<Arguments> getParameters() {
            return Stream.of(
                    Arguments.of(StreamingTestFixture.create(new Handler())),
                    Arguments.of(TestFixture.create(new Handler())));
        }

        private static class Handler {
            @HandleCommand
            void handle(Object command) {
                FluxCapacitor.loadAggregate(parentId, Parent.class).assertLegal(command).apply(command);
            }
        }

        @EventSourced
        @Value
        private static class Parent {
            String id;

            @Entity
            @With
            Object child;
        }


        @Value
        private static class CreateParent {
            String id;

            @Apply
            Parent apply() {
                return new Parent(id, null);
            }
        }

        @Value
        private static class Child {
            @EntityId
            String id;
            int timesUpdated;
        }


        @Value
        private static class CreateChild {
            String id;

            @Apply
            Child apply() {
                return new Child(id, 0);
            }
        }
    }

    public static class WithEntityIdInParent {

        private final CreateParent createParent = new CreateParent(parentId);
        private final CreateChild createChild = new CreateChild(childId);

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testCreateChild(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent).whenCommand(createChild).expectOnlyEvents(createChild)
                    .verify(() -> assertEquals(new Child(childId, 0),
                            getRepo(testFixture).load(parentId, Parent.class).get().getChild()));
        }

        private static Stream<Arguments> getParameters() {
            return Stream.of(
                    Arguments.of(StreamingTestFixture.create(new Handler())),
                    Arguments.of(TestFixture.create(new Handler())));
        }

        private static class Handler {
            @HandleCommand
            void handle(Object command) {
                FluxCapacitor.loadAggregate(parentId, Parent.class).assertLegal(command).apply(command);
            }
        }

        @EventSourced
        @Value
        private static class Parent {
            String id;

            @Entity(entityId = "id")
            @With
            Object child;
        }


        @Value
        private static class CreateParent {
            String id;

            @Apply
            Parent apply() {
                return new Parent(id, null);
            }
        }

        @Value
        private static class Child {
            String id;
            int timesUpdated;
        }

        @Value
        private static class CreateChild {
            String id;

            @Apply
            Child apply() {
                return new Child(id, 0);
            }
        }
    }
}
