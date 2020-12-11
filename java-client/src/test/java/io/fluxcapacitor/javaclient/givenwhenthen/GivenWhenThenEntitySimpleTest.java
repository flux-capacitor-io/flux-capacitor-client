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
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.With;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

public class GivenWhenThenEntitySimpleTest {
    private static final String parentId = "parent", childId = "child",
            grandChild1Id = "grandChild1", grandChild2Id = "grandChild2";
    private static final CreateParent createParent = new CreateParent(parentId);
    private static final CreateChild createChild = new CreateChild(childId);
    private static final UpdateChild updateChild = new UpdateChild(childId);
    private static final RemoveChild removeChild = new RemoveChild(childId);

    private final TestFixture testFixture = TestFixture.create(new Handler());

    @Test
    void testCreateParent() {
        testFixture.whenCommand(createParent).expectOnlyEvents(createParent);
    }

    @Test
    void testCreateChild() {
        testFixture.givenCommands(createParent).whenCommand(createChild).expectOnlyEvents(createChild);
    }

    @Test
    void testCreateChildWithoutParentForbidden() {
        testFixture.whenCommand(createChild).expectException(IllegalStateException.class);
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testCreateChildTwiceForbidden() {
        testFixture.givenCommands(createParent, createChild).whenCommand(createChild).expectException(IllegalStateException.class);
    }

    @Test
    void testUpdateChild() {
        testFixture.givenCommands(createParent, createChild).whenCommand(updateChild).expectOnlyEvents(updateChild);
    }

    @Test
    void testRemoveChild() {
        testFixture.givenCommands(createParent, createChild, updateChild).whenCommand(removeChild).expectOnlyEvents(removeChild);
    }

    @Test
    void testCreateChildAfterRemove() {
        testFixture.givenCommands(createParent, createChild, removeChild).whenCommand(createChild).expectOnlyEvents(createChild);
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

        @Entity(updateMethod = "updateChild")
        Child child;

        Parent updateChild(Child child) {
            return toBuilder().child(child).build();
        }
    }


    @Value
    @Builder(toBuilder = true)
    private static class Child {
        @EntityId
        String id;

        @Entity(entityId = "id")
        @With
        @Singular
        List<GrandChild> grandChildren;
    }

    @Value
    @Builder(toBuilder = true)
    private static class GrandChild {
        String id;

        int timesUpdated;
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
    private static class CreateChild {
        String id;

        @AssertLegal
        void hasParent(Parent parent) {
            if (parent == null) {
                throw new IllegalStateException();
            }
        }

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

        @Apply
        Child apply(Child child) {
            return child.toBuilder().id(id).build();
        }
    }


    @Value
    private static class RemoveChild {
        String id;

        @Apply
        Child apply(Child child) {
            return null;
        }
    }


    @Value
    private static class CreateGrandChild {
        String id;

        @AssertLegal
        void doesNotExists(GrandChild entity) {
            if (entity != null) {
                throw new IllegalStateException();
            }
        }

        @Apply
        GrandChild apply() {
            return GrandChild.builder().id(id).build();
        }
    }

    @Value
    private static class UpdateGrandChild {
        String id;

        @AssertLegal
        void exists(GrandChild entity) {
            if (entity == null) {
                throw new IllegalStateException();
            }
        }

        @Apply
        GrandChild apply(GrandChild entity) {
            return entity.toBuilder().id(id).timesUpdated(entity.getTimesUpdated() + 1).build();
        }
    }

}
