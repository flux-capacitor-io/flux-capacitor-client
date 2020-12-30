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
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


public class GivenWhenThenEntityIdTest {
    private static final String parentId = "parent", childId = "child";

    @Disabled("disabled while working on this feature")
    public static class WithEntityIdInChild {

        private static final TestFixture testFixture = TestFixture.create(new Handler());
        private final CreateParent createParent = new CreateParent(parentId);
        private final CreateChild createChild = new CreateChild(childId);

        @Test
        void testCreateChild() {
            testFixture.givenCommands(createParent, createChild).whenQuery(new GetParent())
                    .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                            .child(Child.builder().id(childId).build()).build()));
        }

        private static class Handler {
            @HandleCommand
            void handle(Object command) {
                FluxCapacitor.loadAggregate(parentId, Parent.class).assertLegal(command).apply(command);
            }

            @HandleQuery
            Parent handle(GetParent query) {
                return FluxCapacitor.loadAggregate(parentId, Parent.class).get();
            }

        }

        @EventSourced
        @Value
        @Builder(toBuilder = true)
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
        @Builder(toBuilder = true)
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

    @Disabled("disabled while working on this feature")
    public static class WithEntityIdInParent {

        private final CreateParent createParent = new CreateParent(parentId);
        private final CreateChild createChild = new CreateChild(childId);

        private static final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testCreateChild() {
            testFixture.givenCommands(createParent, createChild).whenQuery(new GetParent())
                    .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                            .child(Child.builder().id(childId).build()).build()));
        }

        private static class Handler {
            @HandleCommand
            void handle(Object command) {
                FluxCapacitor.loadAggregate(parentId, Parent.class).assertLegal(command).apply(command);
            }

            @HandleQuery
            Parent handle(GetParent query) {
                return FluxCapacitor.loadAggregate(parentId, Parent.class).get();
            }

        }

        @EventSourced
        @Value
        @Builder
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
        @Builder
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

    @Value
    private static class GetParent {
    }
}
