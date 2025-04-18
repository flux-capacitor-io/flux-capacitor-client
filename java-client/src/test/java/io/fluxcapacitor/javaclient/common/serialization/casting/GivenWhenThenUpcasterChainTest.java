/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

package io.fluxcapacitor.javaclient.common.serialization.casting;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingException;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.fluxcapacitor.common.serialization.JsonUtils.fromFile;
import static org.wildfly.common.Assert.assertTrue;

public class GivenWhenThenUpcasterChainTest {
    private static final String aggregateId = "test";

    private final TestFixture testFixture = TestFixture.create(new Handler()).registerCasters(new JsonNodeUpcaster());

    @Test
    void testUpcastingWithDataInput() {
        testFixture.givenAppliedEvents(aggregateId, TestModel.class, "create-model-revision-0.json")
                .whenQuery(new GetModel()).expectResult(new TestModel(List.of(new CreateModel("patchedContent"))));
    }

    @Test
    void upcastCommand() {
        testFixture.whenCommand("create-model-revision-0.json")
                .expectEvents(new CreateModel("patchedContent"));
    }

    @Test
    void droppedCommandDoesNotInvokeHandler() {
        testFixture.whenExecuting(fc -> FluxCapacitor.sendAndForgetCommand(
                        testFixture.parseObject("dropped-create-model-revision-0.json", getClass())))
                .expectNoEvents().expectNoErrors();
    }

    @Test
    void testApplyingUnknownTypeThrows() {
        testFixture
                .given(fc -> fc.client().getEventStoreClient().storeEvents(
                        "test", List.of((SerializedMessage) fromFile("create-model-unknown-type.json")), true))
                .whenApplying(fc -> FluxCapacitor.loadAggregate("test", TestModel.class))
                .expectExceptionalResult(EventSourcingException.class);
    }

    @Test
    void testApplyingUnknownTypeIgnoredIfConfigured() {
        testFixture
                .given(fc -> fc.client().getEventStoreClient().storeEvents(
                        "test", List.of((SerializedMessage) fromFile("create-model-unknown-type.json")), true))
                .whenApplying(fc -> FluxCapacitor.loadAggregate("test", TestModelIgnoringUnknownEvent.class))
                .<Entity<TestModelIgnoringUnknownEvent>>expectResult(
                        e -> e != null && e.type().equals(TestModelIgnoringUnknownEvent.class)
                             && assertTrue(e.isEmpty()) && e.sequenceNumber() == 0L);
    }

    public static class JsonNodeUpcaster {
        @Upcast(type = "io.fluxcapacitor.javaclient.common.serialization.casting.GivenWhenThenUpcasterChainTest$CreateModel",
                revision = 0)
        public ObjectNode upcast(ObjectNode input) {
            return input.put("content", "patchedContent");
        }

        @Upcast(type = "io.fluxcapacitor.javaclient.common.serialization.casting.GivenWhenThenUpcasterChainTest$DroppedCreateModel",
                revision = 0)
        public ObjectNode drop(ObjectNode input) {
            return null;
        }
    }

    @Value
    public static class DroppedCreateModel {
        String content;
    }

    @Value
    @Revision(1)
    public static class CreateModel {
        String content;
    }

    @Value
    @Revision(3)
    public static class UpdateModel {
        String one, two;
    }

    @Value
    public static class GetModel {
    }

    private static class Handler {
        @HandleCommand
        void handle(@NonNull Object command, Metadata metadata) {
            FluxCapacitor.loadAggregate(aggregateId, TestModel.class).assertLegal(command).apply(command, metadata);
        }

        @HandleQuery
        TestModel handle(GetModel query) {
            return FluxCapacitor.loadAggregate(aggregateId, TestModel.class).get();
        }

    }

    @Aggregate
    @Value
    @Builder(toBuilder = true)
    public static class TestModel {
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
        @Singular
        List<Object> events;

        @Apply
        public static TestModel handle(CreateModel event) {
            return new TestModel(List.of(event));
        }

        @Apply
        public TestModel handle(UpdateModel event) {
            return toBuilder().event(event).build();
        }
    }

    @Aggregate(ignoreUnknownEvents = true)
    public static class TestModelIgnoringUnknownEvent {
    }
}
