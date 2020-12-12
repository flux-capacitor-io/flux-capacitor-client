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
import io.fluxcapacitor.javaclient.test.AbstractTestFixture;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.test.streaming.StreamingTestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.givenwhenthen.GivenWhenThenTestUtils.getRepo;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GivenWhenThenEntityPlacementTest {
    private static final String parentId = "parent", childId = "child", childId2 = "child2", grandParentId = "parent";
    private static final CreateGrandParent createGrandParent = new CreateGrandParent(grandParentId);
    private static final CreateChild createChild = new CreateChild(childId);
    private static final CreateChild createChild2 = new CreateChild(childId2);
    private static final UpdateChild updateChild = new UpdateChild(childId);
    private static final RemoveChild removeChild = new RemoveChild(childId);
    private static final String grandParentParameters = "io.fluxcapacitor.javaclient.givenwhenthen.GivenWhenThenEntityPlacementTest#getGrandParentParameters";


    public static class WithChildrenInAList {
        private static final CreateParent createParent = new CreateParent(parentId);

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testAddChildToParentsList(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent).whenCommand(createChild).expectOnlyEvents(createChild)
                    .verify(() -> assertEquals(singletonList(childId),
                            getRepo(testFixture).load(parentId, Parent.class).get()
                                    .getChildren().stream().map(Child::getId).collect(toList())));
        }

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testAddSecondChildToParentsList(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent, createChild).whenCommand(createChild2).expectOnlyEvents(createChild2)
                    .verify(() -> assertEquals(asList(childId, childId2),
                            getRepo(testFixture).load(parentId, Parent.class).get()
                                    .getChildren().stream().map(Child::getId).collect(toList())));
        }


        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testUpdateChildInParentsList(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent, createChild, createChild2).whenCommand(updateChild).expectOnlyEvents(updateChild)
                    .verify(() -> assertEquals(asList(
                            Child.builder().id(childId).timesUpdated(1).build(),
                            Child.builder().id(childId2).build()),
                            getRepo(testFixture).load(parentId, Parent.class).get().getChildren()));
        }

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testRemoveChildFromParentsList(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent, createChild, createChild2).whenCommand(removeChild).expectOnlyEvents(removeChild)
                    .verify(() -> assertEquals(singletonList(childId2),
                            getRepo(testFixture).load(parentId, Parent.class).get()
                                    .getChildren().stream().map(Child::getId).collect(toList())));
        }

        @TestWithParameters
       @Disabled("disabled while working on this feature")
        void testAddChildAgainToParentsList(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent, createChild, createChild2, removeChild).whenCommand(createChild).expectOnlyEvents(createChild)
                    .verify(() -> assertEquals(asList(childId, childId2),
                            getRepo(testFixture).load(parentId, Parent.class).get()
                                    .getChildren().stream().map(Child::getId).collect(toList())));
        }

        @ParameterizedTest
        @MethodSource(grandParentParameters)
        @Disabled("disabled while working on this feature")
        void testUpdateChildListOfGrandParent(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createGrandParent, createParent, createChild)
                    .whenCommand(updateChild).expectOnlyEvents(updateChild)
                    .verify(() -> assertEquals(Parent.builder().id(parentId)
                                    .children(singletonList(Child.builder().id(childId).timesUpdated(1).build())).build(),
                            getRepo(testFixture).load(grandParentId, GrandParent.class).get().getParent()));
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

    public static class WithChildrenInAMap {
        private static final CreateParent createParent = new CreateParent(parentId);

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testAddChildToParentsMap(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent).whenCommand(createChild).expectOnlyEvents(createChild)
                    .verify(() -> assertEquals(singletonList(childId),
                            new ArrayList<>(getRepo(testFixture).load(parentId, Parent.class).get()
                                    .getChildren().keySet())));
        }

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testAddSecondChildToParentsMap(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent, createChild).whenCommand(createChild2).expectOnlyEvents(createChild2)
                    .verify(() -> assertEquals(asList(childId, childId2),
                            new ArrayList<>(getRepo(testFixture).load(parentId, Parent.class).get()
                                    .getChildren().keySet())));
        }

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testUpdateChildInParentsMap(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent, createChild, createChild2).whenCommand(updateChild).expectOnlyEvents(updateChild)
                    .verify(() -> assertEquals(asList(
                            Child.builder().id(childId).timesUpdated(1).build(),
                            Child.builder().id(childId2).build()),
                            new ArrayList<>(getRepo(testFixture).load(parentId, Parent.class).get().getChildren().values())));
        }

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testRemoveChildFromParentsMap(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent, createChild, createChild2).whenCommand(removeChild).expectOnlyEvents(removeChild)
                    .verify(() -> assertEquals(singletonList(childId2),
                            new ArrayList<>(getRepo(testFixture).load(parentId, Parent.class).get()
                                    .getChildren().keySet())));
        }

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testAddChildAgainToParentsMap(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent, createChild, createChild2, removeChild).whenCommand(createChild).expectOnlyEvents(createChild)
                    .verify(() -> assertEquals(asList(childId, childId2),
                            new ArrayList<>(getRepo(testFixture).load(parentId, Parent.class).get()
                                    .getChildren().keySet())));
        }

        @ParameterizedTest
        @MethodSource(grandParentParameters)
        @Disabled("disabled while working on this feature")
        void testUpdateChildMapOfGrandParent(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createGrandParent, createParent, createChild)
                    .whenCommand(updateChild).expectOnlyEvents(updateChild)
                    .verify(() -> assertEquals(Parent.builder().id(parentId)
                                    .children(singletonMap(childId, Child.builder().id(childId).timesUpdated(1).build())).build(),
                            getRepo(testFixture).load(grandParentId, GrandParent.class).get().getParent()));
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
        @Builder(toBuilder = true)
        private static class Parent {
            String id;

            @Entity
            @With
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

    public static class WithChildInAnUnknownObject {
        private static final CreateParent createParent = new CreateParent(parentId);

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testAddUnknownChildToParent(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent).whenCommand(createChild).expectOnlyEvents(createChild)
                    .verify(() -> assertEquals(childId,
                            ((Child) getRepo(testFixture).load(parentId, Parent.class).get().getChild()).getId()));
        }

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testUpdateUnknownChildInParent(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent, createChild).whenCommand(updateChild).expectOnlyEvents(updateChild)
                    .verify(() -> assertEquals(Child.builder().id(childId).timesUpdated(1).build(),
                            getRepo(testFixture).load(parentId, Parent.class).get().getChild()));
        }

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testRemoveUnknownChildFromParent(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent, createChild).whenCommand(removeChild).expectOnlyEvents(removeChild)
                    .verify(() -> assertNull(
                            getRepo(testFixture).load(parentId, Parent.class).get().getChild()));
        }

        @TestWithParameters
        @Disabled("disabled while working on this feature")
        void testAddOtherUnknownChildToParent(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createParent, createChild, removeChild).whenCommand(createChild2).expectOnlyEvents(createChild2)
                    .verify(() -> assertEquals(childId2,
                            ((Child) getRepo(testFixture).load(parentId, Parent.class).get().getChild()).getId()));
        }

        @ParameterizedTest
        @MethodSource(grandParentParameters)
        @Disabled("disabled while working on this feature")
        void testAddChildToGrandParent(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createGrandParent, createParent).whenCommand(createChild).expectOnlyEvents(createChild)
                    .verify(() -> assertEquals(Parent.builder().id(parentId)
                                    .child(Child.builder().id(childId).build()).build(),
                            getRepo(testFixture).load(grandParentId, GrandParent.class).get().getParent()));
        }

        @ParameterizedTest
        @MethodSource(grandParentParameters)
        @Disabled("disabled while working on this feature")
        void testUpdateChildOfGrandParent(AbstractTestFixture testFixture) {
            testFixture.givenCommands(createGrandParent, createParent, createChild)
                    .whenCommand(updateChild).expectOnlyEvents(updateChild)
                    .verify(() -> assertEquals(Parent.builder().id(parentId)
                                    .child(Child.builder().id(childId).timesUpdated(1).build()).build(),
                            getRepo(testFixture).load(grandParentId, GrandParent.class).get().getParent()));
        }

        private static Stream<Arguments> getParameters() {
            return Stream.of(Arguments.of(StreamingTestFixture.create(new Handler())), Arguments.of(TestFixture.create(new Handler())));
        }

        private static class Handler {
            @HandleCommand
            void handle(Object command) {
                FluxCapacitor.loadAggregate(parentId, Parent.class).assertLegal(command).apply(command);
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

    private static Stream<Arguments> getGrandParentParameters() {
        return Stream.of(Arguments.of(TestFixture.create(new GrandParentHandler())),
                Arguments.of(StreamingTestFixture.create(new GrandParentHandler())));
    }

    private static class GrandParentHandler {
        @HandleCommand
        void handle(Object command) {
            FluxCapacitor.loadAggregate(grandParentId, GrandParent.class).assertLegal(command).apply(command);
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
