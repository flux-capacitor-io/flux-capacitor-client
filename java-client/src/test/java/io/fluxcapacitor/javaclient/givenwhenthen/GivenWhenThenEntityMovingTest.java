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
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.givenwhenthen.GivenWhenThenTestUtils.getRepo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class GivenWhenThenEntityMovingTest {
    private static final String parentId = "parent", parentId2 = "parent2", adoptiveParentId = "adoptiveParent", childId = "child";
    private static final CreateParent createParent = new CreateParent(parentId);
    private static final CreateParent createParent2 = new CreateParent(parentId2);
    private static final CreateAdoptiveParent createAdoptiveParent = new CreateAdoptiveParent(adoptiveParentId);
    private static final CreateChild createChild = new CreateChild(childId);
    private static final UpdateChild updateChild = new UpdateChild(childId);
    private static final MoveChild moveChildToFirstParent = new MoveChild(parentId, childId);
    private static final MoveChild moveChildToSecondParent = new MoveChild(parentId2, childId);
    private static final MoveChild moveChildToAdoptiveParent = new MoveChild(adoptiveParentId, childId);

    // Advanced entity feature for later

    @TestWithParameters
    @Disabled("disabled while working on this feature")
    void testMoveChildToSimilarParent(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParent, createParent2, createChild)
                .whenCommand(moveChildToSecondParent).expectOnlyEvents(moveChildToSecondParent)
                .verify(() -> {
                    assertNull(getRepo(testFixture).load(parentId, Parent.class).get().getChild());
                    assertEquals(Child.builder().id(childId).timesUpdated(1).build(),
                            getRepo(testFixture).load(parentId2, Parent.class).get().getChild());
                });
    }

    @TestWithParameters
    @Disabled("disabled while working on this feature")
    void testUpdateMovedChild(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParent, createParent2, createChild, moveChildToSecondParent)
                .whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> {
                    assertNull(getRepo(testFixture).load(parentId, Parent.class).get().getChild());
                    assertEquals(Child.builder().id(childId).timesUpdated(2).build(),
                            getRepo(testFixture).load(parentId2, Parent.class).get().getChild());
                });
    }


    @TestWithParameters
    @Disabled("disabled while working on this feature")
    void testMoveChildToSimilarParentAndBack(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParent, createParent2, createChild, moveChildToSecondParent, updateChild)
                .whenCommand(moveChildToFirstParent).expectOnlyEvents(moveChildToFirstParent)
                .verify(() -> {
                    assertEquals(Child.builder().id(childId).timesUpdated(3).build(),
                            getRepo(testFixture).load(parentId, Parent.class).get().getChild());
                    assertNull(getRepo(testFixture).load(parentId2, Parent.class).get().getChild());
                });
    }

    @TestWithParameters
    @Disabled("disabled while working on this feature")
    void testMoveChildToDifferentParent(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParent, createAdoptiveParent, createChild)
                .whenCommand(moveChildToAdoptiveParent).expectOnlyEvents(moveChildToAdoptiveParent)
                .verify(() -> {
                    assertNull(getRepo(testFixture).load(parentId, Parent.class).get().getChild());
                    assertEquals(Child.builder().id(childId).timesUpdated(1).build(),
                            getRepo(testFixture).load(adoptiveParentId, Parent.class).get().getChild());
                });
    }

    @TestWithParameters
    @Disabled("disabled while working on this feature")
    void testUpdateMovedChildOnDifferentParent(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParent, createAdoptiveParent, createChild, moveChildToAdoptiveParent)
                .whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> {
                    assertNull(getRepo(testFixture).load(parentId, Parent.class).get().getChild());
                    assertEquals(Child.builder().id(childId).timesUpdated(2).build(),
                            getRepo(testFixture).load(adoptiveParentId, Parent.class).get().getChild());
                });
    }


    @TestWithParameters
    @Disabled("disabled while working on this feature")
    void testMoveChildToDifferentParentAndBack(AbstractTestFixture testFixture) {
        testFixture.givenCommands(createParent, createAdoptiveParent, createChild, moveChildToAdoptiveParent, updateChild)
                .whenCommand(moveChildToFirstParent).expectOnlyEvents(moveChildToFirstParent)
                .verify(() -> {
                    assertEquals(Child.builder().id(childId).timesUpdated(3).build(),
                            getRepo(testFixture).load(parentId, Parent.class).get().getChild());
                    assertNull(getRepo(testFixture).load(adoptiveParentId, Parent.class).get().getChild());
                });
    }


    private static Stream<Arguments> getParameters() {
        return Stream.of(Arguments.of(TestFixture.create(new Handler())),
                Arguments.of(StreamingTestFixture.create(new Handler())));
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

    @EventSourced
    @Value
    @Builder(toBuilder = true)
    private static class AdoptiveParent {
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


    @Value
    private static class CreateAdoptiveParent {
        String id;

        @Apply
        Parent apply() {
            return Parent.builder().id(id).build();
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
            return Child.builder().id(id).build();
        }
    }


    @Value
    private static class UpdateChild {
        String id;

        @Apply
        Child apply(Child child) {
            return child.toBuilder().timesUpdated(child.getTimesUpdated() + 1).build();
        }
    }

    @Value
    private static class MoveChild {
        String parentId;
        String id;

        @Apply
        Child apply(Child child) {
            return child.toBuilder().timesUpdated(child.getTimesUpdated() + 1).build();
        }
    }
}
