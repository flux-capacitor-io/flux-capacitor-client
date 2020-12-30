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
import io.fluxcapacitor.javaclient.test.When;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("disabled while working on this feature")
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

    private static final TestFixture testFixture = TestFixture.create(new Handler());

    // Advanced entity feature for later

    @Test
    void testMoveChildToSimilarParent() {
        When givenCommands = testFixture.givenCommands(createParent, createParent2, createChild, moveChildToSecondParent);
        givenCommands.whenQuery(new GetParent(parentId))
                .expectResult(parent -> parent.equals(Parent.builder().id(parentId).build()));
        givenCommands.whenQuery(new GetParent(parentId2))
                .expectResult(parent -> parent.equals(Parent.builder().id(parentId2)
                        .child(Child.builder().id(childId).timesUpdated(1).build()).build()));
    }

    @Test
    void testUpdateMovedChild() {
        When givenCommands = testFixture.givenCommands(createParent, createParent2, createChild, moveChildToSecondParent, updateChild);
        givenCommands.whenQuery(new GetParent(parentId))
                .expectResult(parent -> parent.equals(Parent.builder().id(parentId).build()));
        givenCommands.whenQuery(new GetParent(parentId2))
                .expectResult(parent -> parent.equals(Parent.builder().id(parentId2)
                        .child(Child.builder().id(childId).timesUpdated(2).build()).build()));
    }


    @Test
    void testMoveChildToSimilarParentAndBack() {
        When givenCommands = testFixture.givenCommands(createParent, createParent2, createChild, moveChildToSecondParent,
                updateChild, moveChildToFirstParent);
        givenCommands.whenQuery(new GetParent(parentId))
                .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                        .child(Child.builder().id(childId).timesUpdated(3).build()).build()));
        givenCommands.whenQuery(new GetParent(parentId2))
                .expectResult(parent -> parent.equals(Parent.builder().id(parentId2).build()));
    }

    @Test
    void testMoveChildToDifferentParent() {
        When givenCommands = testFixture.givenCommands(createParent, createAdoptiveParent, createChild, moveChildToAdoptiveParent);
        givenCommands.whenQuery(new GetParent(parentId))
                .expectResult(parent -> parent.equals(Parent.builder().id(parentId).build()));
        givenCommands.whenQuery(new GetParent(adoptiveParentId))
                .expectResult(parent -> parent.equals(Parent.builder().id(adoptiveParentId)
                        .child(Child.builder().id(childId).timesUpdated(1).build()).build()));
    }

    @Test
    void testUpdateMovedChildOnDifferentParent() {
        When givenCommands = testFixture.givenCommands(createParent, createAdoptiveParent, createChild,
                moveChildToAdoptiveParent, updateChild);
        givenCommands.whenQuery(new GetParent(parentId))
                .expectResult(parent -> parent.equals(Parent.builder().id(parentId).build()));
        givenCommands.whenQuery(new GetParent(adoptiveParentId))
                .expectResult(parent -> parent.equals(Parent.builder().id(adoptiveParentId)
                        .child(Child.builder().id(childId).timesUpdated(2).build()).build()));
    }


    @Test
    void testMoveChildToDifferentParentAndBack() {
        When givenCommands = testFixture.givenCommands(createParent, createAdoptiveParent, createChild,
                moveChildToAdoptiveParent, updateChild, moveChildToFirstParent);
        givenCommands.whenQuery(new GetParent(parentId))
                .expectResult(parent -> parent.equals(Parent.builder().id(parentId)
                        .child(Child.builder().id(childId).timesUpdated(3).build()).build()));
        givenCommands.whenQuery(new GetParent(adoptiveParentId))
                .expectResult(parent -> parent.equals(Parent.builder().id(adoptiveParentId).build()));
    }


    private static class Handler {
        @HandleCommand
        void handle(Object command) {
            FluxCapacitor.loadAggregate(parentId, Parent.class).assertLegal(command).apply(command);
        }

        @HandleQuery
        Parent handle(GetParent query) {
            return FluxCapacitor.loadAggregate(query.getId(), Parent.class).get();
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
    private static class GetParent {
        String id;
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
