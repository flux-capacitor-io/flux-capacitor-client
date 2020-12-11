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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GivenWhenThenEntityApplyTest {
    private static final String parentId = "parent", childId = "child";
    private static final CreateParent createParent = new CreateParent(parentId);
    private static final CreateChild createChild = new CreateChild(childId);
    private static final UpdateChild updateChild = new UpdateChild(childId);
    private static final UpsertChild upsertChild = new UpsertChild(childId);
    private static final CreateChildNoApply createChildNoApply = new CreateChildNoApply(childId);
    private static final UpdateChildNoApply updateChildNoApply = new UpdateChildNoApply(childId);

    private final TestFixture testFixture = TestFixture.create(new Handler());


    @Test
    @Disabled("disabled while working on this feature")
    void testCreateChildWithApplyInEvent() {
        testFixture.givenCommands(createParent).whenCommand(createChild).expectOnlyEvents(createChild)
                .verify(() -> assertEquals(Child.builder().id(childId).build(),
                        getRepo(testFixture).load(parentId, Parent.class).get().getChild()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testUpdateChildWithApplyInEvent() {
        testFixture.givenCommands(createParent, createChild).whenCommand(updateChild).expectOnlyEvents(updateChild)
                .verify(() -> assertEquals(Child.builder().id(childId).timesUpdated(1).build(),
                        getRepo(testFixture).load(parentId, Parent.class).get().getChild()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testUpsertChildWithApplyInEvent() {
        testFixture.givenCommands(createParent).whenCommand(upsertChild).expectOnlyEvents(upsertChild)
                .verify(() -> assertEquals(Child.builder().id(childId).build(),
                        getRepo(testFixture).load(parentId, Parent.class).get().getChild()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testUpsertChildTwiceWithApplyInEvent() {
        testFixture.givenCommands(createParent, upsertChild).whenCommand(upsertChild).expectOnlyEvents(upsertChild)
                .verify(() -> assertEquals(Child.builder().id(childId).timesUpdated(1).build(),
                        getRepo(testFixture).load(parentId, Parent.class).get().getChild()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testCreateChildWithApplyInModel() {
        testFixture.givenCommands(createParent).whenCommand(createChildNoApply).expectOnlyEvents(createChildNoApply)
                .verify(() -> assertEquals(ChildWithApply.builder().id(childId).build(),
                        getRepo(testFixture).load(parentId, Parent.class).get().getChild()));
    }

    @Test
    @Disabled("disabled while working on this feature")
    void testUpdateChildWithApplyInModel() {
        testFixture.givenCommands(createParent, createChildNoApply).whenCommand(updateChildNoApply).expectOnlyEvents(updateChildNoApply)
                .verify(() -> assertEquals(ChildWithApply.builder().id(childId).timesUpdated(1).build(),
                        getRepo(testFixture).load(parentId, Parent.class).get().getChild()));
    }


    AggregateRepository getRepo(TestFixture testFixture) {
        return testFixture.getFluxCapacitor().aggregateRepository();
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

    @Value
    @Builder(toBuilder = true)
    private static class Child {
        @EntityId
        String id;
        int timesUpdated;
    }


    @Value
    @Builder(toBuilder = true)
    private static class ChildWithApply {
        @EntityId
        String id;
        int timesUpdated;

        @Apply
        ChildWithApply apply(CreateChildNoApply event) {
            return ChildWithApply.builder().id(event.getId()).build();
        }

        @Apply
        ChildWithApply apply(UpdateChildNoApply event) {
            return this.toBuilder().timesUpdated(getTimesUpdated() + 1).build();
        }
    }


    @Value
    private static class CreateChildNoApply {
        String id;
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
    private static class UpdateChildNoApply {
        String id;
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
    private static class UpsertChild {
        String id;

        @Apply
        Child apply() {
            return Child.builder().id(id).build();
        }

        @Apply
        Child apply(Child child) {
            return child.toBuilder().timesUpdated(child.getTimesUpdated() + 1).build();
        }
    }
}
