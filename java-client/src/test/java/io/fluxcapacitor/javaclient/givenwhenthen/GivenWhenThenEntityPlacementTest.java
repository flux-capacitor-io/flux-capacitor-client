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
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private final TestFixture testFixtureForList = TestFixture.create(new ParentOfListHandler());
    private final TestFixture testFixtureForMap = TestFixture.create(new ParentOfMapHandler());
    private final TestFixture testFixtureForUnknown = TestFixture.create(new ParentOfUnknownHandler());
    private final TestFixture testFixtureForGrandParent = TestFixture.create(new ParentOfListHandler());

    @Test
    @Disabled("disabled while working on this feature")
    void testAddChildToParentsList() {
        testFixtureForList.givenCommands(createParentOfList).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(singletonList(childId),
                        getRepo(testFixtureForList).load(parentId, ParentOfList.class).get()
                                .getChildren().stream().map(Child::getId).collect(toList())));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testAddSecondChildToParentsList() {
        testFixtureForList.givenCommands(createParentOfList, createChild).whenCommand(createChild2).expectOnlyEvents(createChild2)
                .verify(() -> assertEquals(asList(childId, childId2),
                        getRepo(testFixtureForList).load(parentId, ParentOfList.class).get()
                                .getChildren().stream().map(Child::getId).collect(toList())));
    }


    @Test
    @Disabled("disabled while working on this feature")
    void testUpdateChildInParentsList() {
        testFixtureForList.givenCommands(createParentOfList, createChild, createChild2).whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(asList(
                        Child.builder().id(childId).timesUpdated(1).build(),
                        Child.builder().id(childId2).build()),
                        getRepo(testFixtureForList).load(parentId, ParentOfList.class).get().getChildren()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testRemoveChildFromParentsList() {
        testFixtureForList.givenCommands(createParentOfList, createChild, createChild2).whenCommand(removeChild).expectOnlyEvents(removeChild)
                .verify(() -> assertEquals(singletonList(childId2),
                        getRepo(testFixtureForList).load(parentId, ParentOfList.class).get()
                                .getChildren().stream().map(Child::getId).collect(toList())));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testAddChildAgainToParentsList() {
        testFixtureForList.givenCommands(createParentOfList, createChild, createChild2, removeChild).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(asList(childId, childId2),
                        getRepo(testFixtureForList).load(parentId, ParentOfList.class).get()
                                .getChildren().stream().map(Child::getId).collect(toList())));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testAddChildToParentsMap() {
        testFixtureForMap.givenCommands(createParentOfMap).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(singletonList(childId),
                        new ArrayList<>(getRepo(testFixtureForMap).load(parentId, ParentOfMap.class).get()
                                .getChildren().keySet())));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testAddSecondChildToParentsMap() {
        testFixtureForMap.givenCommands(createParentOfMap, createChild).whenCommand(createChild2).expectOnlyEvents(createChild2)
                .verify(() -> assertEquals(asList(childId, childId2),
                        new ArrayList<>(getRepo(testFixtureForMap).load(parentId, ParentOfMap.class).get()
                                .getChildren().keySet())));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testUpdateChildInParentsMap() {
        testFixtureForMap.givenCommands(createParentOfList, createChild, createChild2).whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(asList(
                        Child.builder().id(childId).timesUpdated(1).build(),
                        Child.builder().id(childId2).build()),
                        new ArrayList<>(getRepo(testFixtureForMap).load(parentId, ParentOfMap.class).get().getChildren().values())));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testRemoveChildFromParentsMap() {
        testFixtureForMap.givenCommands(createParentOfMap, createChild, createChild2).whenCommand(removeChild).expectOnlyEvents(removeChild)
                .verify(() -> assertEquals(singletonList(childId2),
                        new ArrayList<>(getRepo(testFixtureForMap).load(parentId, ParentOfMap.class).get()
                                .getChildren().keySet())));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testAddChildAgainToParentsMap() {
        testFixtureForMap.givenCommands(createParentOfMap, createChild, createChild2, removeChild).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(asList(childId, childId2),
                        new ArrayList<>(getRepo(testFixtureForMap).load(parentId, ParentOfMap.class).get()
                                .getChildren().keySet())));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testAddUnknownChildToParent() {
        testFixtureForUnknown.givenCommands(createParentOfUnknown).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(childId,
                        ((Child) getRepo(testFixtureForMap).load(parentId, ParentOfUnknown.class).get().getChild()).getId()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testUpdateUnknownChildInParent() {
        testFixtureForUnknown.givenCommands(createParentOfUnknown, createChild).whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(Child.builder().id(childId).timesUpdated(1).build(),
                        getRepo(testFixtureForUnknown).load(parentId, ParentOfUnknown.class).get().getChild()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testRemoveUnknownChildFromParent() {
        testFixtureForUnknown.givenCommands(createParentOfUnknown, createChild).whenCommand(removeChild).expectOnlyEvents(removeChild)
                .verify(() -> assertNull(
                        getRepo(testFixtureForMap).load(parentId, ParentOfUnknown.class).get().getChild()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testAddOtherUnknownChildToParent() {
        testFixtureForUnknown.givenCommands(createParentOfUnknown, createChild, removeChild).whenCommand(createChild2).expectOnlyEvents(createChild2)
                .verify(() -> assertEquals(childId2,
                        ((Child) getRepo(testFixtureForMap).load(parentId, ParentOfUnknown.class).get().getChild()).getId()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testAddChildToGrandParent() {
        testFixtureForGrandParent.givenCommands(createGrandParent, createParentOfUnknown).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(ParentOfUnknown.builder().id(parentId)
                                .child(Child.builder().id(childId).build()).build(),
                        getRepo(testFixtureForGrandParent).load(grandParentId, GrandParent.class).get().getParent()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testUpdateChildOfGrandParent() {
        testFixtureForGrandParent.givenCommands(createGrandParent, createParentOfUnknown, createChild)
                .whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(ParentOfUnknown.builder().id(parentId)
                                .child(Child.builder().id(childId).timesUpdated(1).build()).build(),
                        getRepo(testFixtureForGrandParent).load(grandParentId, GrandParent.class).get().getParent()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testUpdateChildListOfGrandParent() {
        testFixtureForGrandParent.givenCommands(createGrandParent, createParentOfList, createChild)
                .whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(ParentOfList.builder().id(parentId)
                                .children(singletonList(Child.builder().id(childId).timesUpdated(1).build())).build(),
                        getRepo(testFixtureForGrandParent).load(grandParentId, GrandParent.class).get().getParent()));
    }


    @Test
    @Disabled("disabled while working on this feature")
    void testUpdateChildMapOfGrandParent() {
        testFixtureForGrandParent.givenCommands(createGrandParent, createParentOfMap, createChild)
                .whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(ParentOfMap.builder().id(parentId)
                                .children(singletonMap(childId, Child.builder().id(childId).timesUpdated(1).build())).build(),
                        getRepo(testFixtureForGrandParent).load(grandParentId, GrandParent.class).get().getParent()));
    }


    AggregateRepository getRepo(TestFixture testFixture) {
        return testFixture.getFluxCapacitor().aggregateRepository();
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
