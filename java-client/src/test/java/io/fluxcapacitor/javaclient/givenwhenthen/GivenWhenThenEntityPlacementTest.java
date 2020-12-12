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
import io.fluxcapacitor.javaclient.modeling.AggregateRepository;
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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GivenWhenThenEntityPlacementTest {
    private static final String parentId = "parent", childId = "child", childId2 = "child2", grandParentId = "parent";
    private static final CreateParentOfList createParentOfList = new CreateParentOfList(parentId);
    private static final CreateParentOfMap createParentOfMap = new CreateParentOfMap(parentId);
    private static final CreateParentOfUnknown createParentOfUnknown = new CreateParentOfUnknown(parentId);
    private static final CreateGrandParent createGrandParent = new CreateGrandParent(grandParentId);
    private static final CreateChild createChild = new CreateChild(childId);
    private static final CreateChild createChild2 = new CreateChild(childId2);
    private static final UpdateChild updateChild = new UpdateChild(childId);
    private static final RemoveChild removeChild = new RemoveChild(childId);

    @ParameterizedTest
    @MethodSource("listParams")
    @Disabled("disabled while working on this feature")
    void testAddChildToParentsList(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfList).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(singletonList(childId),
                        getRepo(testFixture).load(parentId, ParentOfList.class).get()
                                .getChildren().stream().map(Child::getId).collect(toList())));
    }

    @ParameterizedTest
    @MethodSource("listParams")
    @Disabled("disabled while working on this feature")
    void testAddSecondChildToParentsList(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfList, createChild).whenCommand(createChild2).expectOnlyEvents(createChild2)
                .verify(() -> assertEquals(asList(childId, childId2),
                        getRepo(testFixture).load(parentId, ParentOfList.class).get()
                                .getChildren().stream().map(Child::getId).collect(toList())));
    }


    @ParameterizedTest
    @MethodSource("listParams")
    @Disabled("disabled while working on this feature")
    void testUpdateChildInParentsList(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfList, createChild, createChild2).whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(asList(
                        Child.builder().id(childId).timesUpdated(1).build(),
                        Child.builder().id(childId2).build()),
                        getRepo(testFixture).load(parentId, ParentOfList.class).get().getChildren()));
    }

    @ParameterizedTest
    @MethodSource("listParams")
    @Disabled("disabled while working on this feature")
    void testRemoveChildFromParentsList(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfList, createChild, createChild2).whenCommand(removeChild).expectOnlyEvents(removeChild)
                .verify(() -> assertEquals(singletonList(childId2),
                        getRepo(testFixture).load(parentId, ParentOfList.class).get()
                                .getChildren().stream().map(Child::getId).collect(toList())));
    }

    @ParameterizedTest
    @MethodSource("listParams")
    @Disabled("disabled while working on this feature")
    void testAddChildAgainToParentsList(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfList, createChild, createChild2, removeChild).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(asList(childId, childId2),
                        getRepo(testFixture).load(parentId, ParentOfList.class).get()
                                .getChildren().stream().map(Child::getId).collect(toList())));
    }


    @ParameterizedTest
    @MethodSource("mapParams")
    @Disabled("disabled while working on this feature")
    void testAddChildToParentsMap(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfMap).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(singletonList(childId),
                        new ArrayList<>(getRepo(testFixture).load(parentId, ParentOfMap.class).get()
                                .getChildren().keySet())));
    }

    @ParameterizedTest
    @MethodSource("mapParams")
    @Disabled("disabled while working on this feature")
    void testAddSecondChildToParentsMap(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfMap, createChild).whenCommand(createChild2).expectOnlyEvents(createChild2)
                .verify(() -> assertEquals(asList(childId, childId2),
                        new ArrayList<>(getRepo(testFixture).load(parentId, ParentOfMap.class).get()
                                .getChildren().keySet())));
    }

    @ParameterizedTest
    @MethodSource("mapParams")
    @Disabled("disabled while working on this feature")
    void testUpdateChildInParentsMap(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfList, createChild, createChild2).whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(asList(
                        Child.builder().id(childId).timesUpdated(1).build(),
                        Child.builder().id(childId2).build()),
                        new ArrayList<>(getRepo(testFixture).load(parentId, ParentOfMap.class).get().getChildren().values())));
    }

    @ParameterizedTest
    @MethodSource("mapParams")
    @Disabled("disabled while working on this feature")
    void testRemoveChildFromParentsMap(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfMap, createChild, createChild2).whenCommand(removeChild).expectOnlyEvents(removeChild)
                .verify(() -> assertEquals(singletonList(childId2),
                        new ArrayList<>(getRepo(testFixture).load(parentId, ParentOfMap.class).get()
                                .getChildren().keySet())));
    }

    @ParameterizedTest
    @MethodSource("mapParams")
    @Disabled("disabled while working on this feature")
    void testAddChildAgainToParentsMap(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfMap, createChild, createChild2, removeChild).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(asList(childId, childId2),
                        new ArrayList<>(getRepo(testFixture).load(parentId, ParentOfMap.class).get()
                                .getChildren().keySet())));
    }

    @ParameterizedTest
    @MethodSource("unknownParams")
    @Disabled("disabled while working on this feature")
    void testAddUnknownChildToParent(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfUnknown).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(childId,
                        ((Child) getRepo(testFixture).load(parentId, ParentOfUnknown.class).get().getChild()).getId()));
    }

    @ParameterizedTest
    @MethodSource("unknownParams")
    @Disabled("disabled while working on this feature")
    void testUpdateUnknownChildInParent(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfUnknown, createChild).whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(Child.builder().id(childId).timesUpdated(1).build(),
                        getRepo(testFixture).load(parentId, ParentOfUnknown.class).get().getChild()));
    }

    @ParameterizedTest
    @MethodSource("unknownParams")
    @Disabled("disabled while working on this feature")
    void testRemoveUnknownChildFromParent(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfUnknown, createChild).whenCommand(removeChild).expectOnlyEvents(removeChild)
                .verify(() -> assertNull(
                        getRepo(testFixture).load(parentId, ParentOfUnknown.class).get().getChild()));
    }

    @ParameterizedTest
    @MethodSource("unknownParams")
    @Disabled("disabled while working on this feature")
    void testAddOtherUnknownChildToParent(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParentOfUnknown, createChild, removeChild).whenCommand(createChild2).expectOnlyEvents(createChild2)
                .verify(() -> assertEquals(childId2,
                        ((Child) getRepo(testFixture).load(parentId, ParentOfUnknown.class).get().getChild()).getId()));
    }

    @ParameterizedTest
    @MethodSource("grandParentParams")
    @Disabled("disabled while working on this feature")
    void testAddChildToGrandParent(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createGrandParent, createParentOfUnknown).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(ParentOfUnknown.builder().id(parentId)
                                .child(Child.builder().id(childId).build()).build(),
                        getRepo(testFixture).load(grandParentId, GrandParent.class).get().getParent()));
    }

    @ParameterizedTest
    @MethodSource("grandParentParams")
    @Disabled("disabled while working on this feature")
    void testUpdateChildOfGrandParent(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createGrandParent, createParentOfUnknown, createChild)
                .whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(ParentOfUnknown.builder().id(parentId)
                                .child(Child.builder().id(childId).timesUpdated(1).build()).build(),
                        getRepo(testFixture).load(grandParentId, GrandParent.class).get().getParent()));
    }

    @ParameterizedTest
    @MethodSource("grandParentParams")
    @Disabled("disabled while working on this feature")
    void testUpdateChildListOfGrandParent(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createGrandParent, createParentOfList, createChild)
                .whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(ParentOfList.builder().id(parentId)
                                .children(singletonList(Child.builder().id(childId).timesUpdated(1).build())).build(),
                        getRepo(testFixture).load(grandParentId, GrandParent.class).get().getParent()));
    }


    @ParameterizedTest
    @MethodSource("grandParentParams")
    @Disabled("disabled while working on this feature")
    void testUpdateChildMapOfGrandParent(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createGrandParent, createParentOfMap, createChild)
                .whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(ParentOfMap.builder().id(parentId)
                                .children(singletonMap(childId, Child.builder().id(childId).timesUpdated(1).build())).build(),
                        getRepo(testFixture).load(grandParentId, GrandParent.class).get().getParent()));
    }


    AggregateRepository getRepo(AbstractTestFixture testFixture) {
        return testFixture.getFluxCapacitor().aggregateRepository();
    }


    private static Stream<Arguments> listParams() {
        return Stream.of(
                Arguments.of(StreamingTestFixture.create(new ParentOfListHandler())),
                Arguments.of(TestFixture.create(new ParentOfListHandler())));
    }

    private static Stream<Arguments> mapParams() {
        return Stream.of(
                Arguments.of(StreamingTestFixture.create(new ParentOfMapHandler())),
                Arguments.of(TestFixture.create(new ParentOfMapHandler())));
    }

    private static Stream<Arguments> unknownParams() {
        return Stream.of(
                Arguments.of(StreamingTestFixture.create(new ParentOfUnknownHandler())),
                Arguments.of(TestFixture.create(new ParentOfUnknownHandler())));
    }

    private static Stream<Arguments> grandParentParams() {
        return Stream.of(
                Arguments.of(StreamingTestFixture.create(new GrandParentHandler())),
                Arguments.of(TestFixture.create(new GrandParentHandler())));
    }

    private static class ParentOfListHandler {
        @HandleCommand
        void handle(Object command) {
            FluxCapacitor.loadAggregate(parentId, ParentOfList.class).assertLegal(command).apply(command);
        }
    }

    private static class ParentOfMapHandler {
        @HandleCommand
        void handle(Object command) {
            FluxCapacitor.loadAggregate(parentId, ParentOfMap.class).assertLegal(command).apply(command);
        }
    }

    private static class ParentOfUnknownHandler {
        @HandleCommand
        void handle(Object command) {
            FluxCapacitor.loadAggregate(parentId, ParentOfUnknown.class).assertLegal(command).apply(command);
        }
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
    private static class ParentOfList {
        String id;

        @Entity
        @With
        List<Child> children;
    }

    @EventSourced
    @Value
    @Builder(toBuilder = true)
    private static class ParentOfMap {
        String id;

        @Entity
        @With
        Map<String, Child> children;
    }

    @EventSourced
    @Value
    @Builder(toBuilder = true)
    private static class ParentOfUnknown {
        String id;

        @Entity
        @With
        Object child;
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
    private static class CreateParentOfList {
        String id;

        @Apply
        ParentOfList apply() {
            return ParentOfList.builder().id(id).build();
        }
    }

    @Value
    private static class CreateParentOfMap {
        String id;

        @Apply
        ParentOfMap apply() {
            return ParentOfMap.builder().id(id).build();
        }
    }

    @Value
    private static class CreateParentOfUnknown {
        String id;

        @Apply
        ParentOfUnknown apply() {
            return ParentOfUnknown.builder().id(id).build();
        }
    }

    @Value
    private static class CreateGrandParent {
        String id;

        @Apply
        GrandParent apply() {
            return GrandParent.builder().id(id).build();
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

}
