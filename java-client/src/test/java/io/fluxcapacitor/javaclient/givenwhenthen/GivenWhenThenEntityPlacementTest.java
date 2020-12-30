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
import io.fluxcapacitor.javaclient.modeling.AssertLegal;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourced;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.With;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class GivenWhenThenEntityPlacementTest {
    private static final String parentId = "parent", childId = "child", childId2 = "child2", grandParentId = "parent";
    private static final CreateGrandParent createGrandParent = new CreateGrandParent(grandParentId);
    private static final CreateChild createChild = new CreateChild(childId);
    private static final CreateChild createChild2 = new CreateChild(childId2);
    private static final UpdateChild updateChild = new UpdateChild(childId);
    private static final RemoveChild removeChild = new RemoveChild(childId);
    private static final TestFixture grandParentTestFixture = TestFixture.create(new GrandParentHandler());

    @Disabled("disabled while working on this feature")
    public static class WithChildrenInAList {
        private static final CreateParent createParent = new CreateParent(parentId);
        private static final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testAddChildToParentsList() {
            testFixture.givenCommands(createParent, createChild).whenQuery(new GetParent())
                    .expectResult(parent -> ((Parent) parent).getChildren().equals(
                            singletonList(Child.builder().id(childId).build())));
        }

        @Test
        void testAddSecondChildToParentsList() {
            testFixture.givenCommands(createParent, createChild, createChild2).whenQuery(new GetParent())
                    .expectResult(parent -> ((Parent) parent).getChildren().equals(
                            asList(Child.builder().id(childId).build(),
                                    Child.builder().id(childId2).build())));
        }


        @Test
        void testUpdateChildInParentsList() {
            testFixture.givenCommands(createParent, createChild, createChild2, updateChild).whenQuery(new GetParent())
                    .expectResult(parent -> ((Parent) parent).getChildren().equals(
                            asList(Child.builder().id(childId).timesUpdated(1).build(),
                                    Child.builder().id(childId2).build())));
        }

        @Test
        void testRemoveChildFromParentsList() {
            testFixture.givenCommands(createParent, createChild, createChild2, removeChild).whenQuery(new GetParent())
                    .expectResult(parent -> ((Parent) parent).getChildren().equals(
                            singletonList(Child.builder().id(childId2).build())));
        }

        @Test
        void testAddChildAgainToParentsList() {
            testFixture.givenCommands(createParent, createChild, createChild2, removeChild, createChild).whenQuery(new GetParent())
                    .expectResult(parent -> ((Parent) parent).getChildren().equals(
                            asList(Child.builder().id(childId).build(),
                                    Child.builder().id(childId2).build())));
        }

        @Test
        void testUpdateChildListOfGrandParent() {
            grandParentTestFixture.givenCommands(createGrandParent, createParent, createChild, updateChild).whenQuery(new GetGrandParent())
                    .expectResult(grandParent -> ((GrandParent) grandParent).getParent().equals(
                            Parent.builder().id(parentId).children(
                                    singletonList(Child.builder().id(childId).timesUpdated(1).build())).build()));
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
            List<Child> children;
        }

        @Value
        private static class CreateParent {
            String id;

            @Apply
            Parent apply() {
                return Parent.builder().id(id).build();
            }
        }

    }

    @Disabled("disabled while working on this feature")
    public static class WithChildrenInAMap {
        private static final CreateParent createParent = new CreateParent(parentId);
        private static final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testAddChildToParentsMap() {
            testFixture.givenCommands(createParent, createChild).whenQuery(new GetParent())
                    .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                            .child(childId, Child.builder().id(childId).build()).build()));
        }

        @Test
        void testAddSecondChildToParentsMap() {
            testFixture.givenCommands(createParent, createChild, createChild2).whenQuery(new GetParent())
                    .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                            .child(childId, Child.builder().id(childId).build())
                            .child(childId2, Child.builder().id(childId2).build()).build()));
        }

        @Test
        void testUpdateChildInParentsMap() {
            testFixture.givenCommands(createParent, createChild, createChild2, updateChild).whenQuery(new GetParent())
                    .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                            .child(childId, Child.builder().id(childId).timesUpdated(1).build())
                            .child(childId2, Child.builder().id(childId2).build()).build()));
        }

        @Test
        void testRemoveChildFromParentsMap() {
            testFixture.givenCommands(createParent, createChild, createChild2, removeChild).whenQuery(new GetParent())
                    .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                            .child(childId2, Child.builder().id(childId2).build()).build()));
        }

        @Test
        void testAddChildAgainToParentsMap() {
            testFixture.givenCommands(createParent, createChild, createChild2, removeChild, createChild).whenQuery(new GetParent())
                    .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                            .child(childId, Child.builder().id(childId).build())
                            .child(childId2, Child.builder().id(childId2).build()).build()));
        }

        @Test
        void testUpdateChildMapOfGrandParent() {
            grandParentTestFixture.givenCommands(createGrandParent, createParent, createChild, updateChild).whenQuery(new GetGrandParent())
                    .expectResult(grandParent -> ((GrandParent) grandParent).getParent().equals(
                            Parent.builder().id(parentId).child(childId,
                                    Child.builder().id(childId).timesUpdated(1).build()).build()));
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
            @Singular
            Map<String, Child> children;
        }

        @Value
        private static class CreateParent {
            String id;

            @Apply
            Parent apply() {
                return Parent.builder().id(id).build();
            }
        }

    }

    @Disabled("disabled while working on this feature")
    public static class WithChildInAnUnknownObject {
        private static final CreateParent createParent = new CreateParent(parentId);
        private static final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testAddUnknownChildToParent() {
            testFixture.givenCommands(createParent, createChild).whenQuery(new GetParent())
                    .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                            .child(Child.builder().id(childId).build()).build()));
        }

        @Test
        void testUpdateUnknownChildInParent() {
            testFixture.givenCommands(createParent, createChild, updateChild).whenQuery(new GetParent())
                    .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                            .child(Child.builder().id(childId).timesUpdated(1).build()).build()));
        }

        @Test
        void testRemoveUnknownChildFromParent() {
            testFixture.givenCommands(createParent, createChild, removeChild).whenQuery(new GetParent())
                    .expectResult(parent -> parent.equals(Parent.builder().id(parentId).build()));
        }

        @Test
        void testAddOtherUnknownChildToParent() {
            testFixture.givenCommands(createParent, createChild, removeChild, createChild2).whenQuery(new GetParent())
                    .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                            .child(Child.builder().id(childId2).build()).build()));
        }

        @Test
        void testAddChildToGrandParent() {
            grandParentTestFixture.givenCommands(createGrandParent, createParent, createChild).whenQuery(new GetGrandParent())
                    .expectResult(grandParent -> ((GrandParent) grandParent).getParent().equals(
                            Parent.builder().id(parentId)
                                    .child(Child.builder().id(childId).build()).build()));
        }

        @Test
        void testUpdateChildOfGrandParent() {
            grandParentTestFixture.givenCommands(createGrandParent, createParent, createChild, updateChild).whenQuery(new GetGrandParent())
                    .expectResult(grandParent -> ((GrandParent) grandParent).getParent().equals(
                            Parent.builder().id(parentId)
                                    .child(Child.builder().id(childId).timesUpdated(1).build()).build()));
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
                return Parent.builder().id(id).build();
            }
        }

    }

    @Value
    private static class GetParent {
    }

    @Value
    private static class GetGrandParent {
    }

    @Value
    private static class CreateChild {
        String id;

        @AssertLegal
        void doesNotExist(Child child) {
            if (child != null) {
                throw new IllegalStateException();
            }
        }

        @Apply
        Child apply() {
            return Child.builder().id(id).build();
        }
    }

    @Value
    private static class UpdateChild {
        String id;

        @AssertLegal
        void doesExist(Child child) {
            if (child == null) {
                throw new IllegalStateException();
            }
        }

        @Apply
        Child apply(Child child) {
            return child.toBuilder().timesUpdated(child.getTimesUpdated() + 1).build();
        }
    }

    @Value
    private static class RemoveChild {
        String id;

        @AssertLegal
        void doesExist(Child child) {
            if (child == null) {
                throw new IllegalStateException();
            }
        }

        @Apply
        Child apply(Child child) {
            return null;
        }
    }


    @Value
    @Builder(toBuilder = true)
    private static class Child {
        @EntityId
        String id;
        int timesUpdated;
    }

    private static class GrandParentHandler {
        @HandleCommand
        void handle(Object command) {
            FluxCapacitor.loadAggregate(grandParentId, GrandParent.class).assertLegal(command).apply(command);
        }

        @HandleQuery
        GrandParent handle(GetGrandParent query) {
            return FluxCapacitor.loadAggregate(grandParentId, GrandParent.class).get();
        }

    }

    @EventSourced
    @Value
    @Builder(toBuilder = true)
    private static class GrandParent {
        String id;

        @Entity
        @With
        Object parent;
    }

    @Value
    private static class CreateGrandParent {
        String id;

        @Apply
        GrandParent apply() {
            return GrandParent.builder().id(id).build();
        }
    }

}
