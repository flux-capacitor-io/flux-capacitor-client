/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.ApplyEvent;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Long.parseLong;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

public class GivenWhenThenCorrelationTest {
    private static final String aggregateId = "test";
    private final TestFixture testFixture = TestFixture.createAsync(new Handler());

    @Test
    void testDefaultCorrelationData() {
        testFixture.givenCommands(new CreateModel())
                .whenQuery(new GetModel())
                .<TestModel>expectResult(r -> r.getEventMetadata().size() == 1 &&
                        ofNullable(r.getEventMetadata().get(0))
                                .map(m -> m.get("$consumer").equals(m.get("$clientName") + "_COMMAND")
                                        && parseLong(m.get("$correlationId")) == parseLong(m.get("$traceId"))
                                        && m.get("$trigger").equals(CreateModel.class.getName())
                                ).orElse(false));
    }

    @Test
    void testDefaultCorrelationDataAfterTwoSteps() {
        testFixture.givenCommands(new CreateModelInTwoSteps())
                .whenQuery(new GetModel())
                .<TestModel>expectResult(r -> r.getEventMetadata().size() == 1 &&
                        ofNullable(r.getEventMetadata().get(0))
                                .map(m -> m.get("$consumer").equals(m.get("$clientName") + "_COMMAND")
                                        && parseLong(m.get("$correlationId")) > parseLong(m.get("$traceId"))
                                        && m.get("$trigger").equals(CreateModel.class.getName())
                                ).orElse(false));
    }

    @Test
    void testCustomTrace() {
        testFixture.givenCommands(new Message(new CreateModel(), Metadata.empty().withTrace("userName", "myself")))
                .whenQuery(new GetModel())
                .<TestModel>expectResult(r -> r.getEventMetadata().size() == 1 &&
                        ofNullable(r.getEventMetadata().get(0))
                                .map(m -> m.get("$trace.userName").equals("myself")).orElse(false));
    }

    @Test
    void testCustomTraceInTwoSteps() {
        testFixture.givenCommands(new Message(new CreateModelInTwoSteps(), Metadata.empty().withTrace("userName", "myself")))
                .whenQuery(new GetModel())
                .<TestModel>expectResult(r -> r.getEventMetadata().size() == 1 &&
                        ofNullable(r.getEventMetadata().get(0))
                                .map(m -> m.get("$trace.userName").equals("myself")).orElse(false));
    }

    private static class Handler {
        @HandleCommand
        void handle(Metadata metadata, Object command) {
            FluxCapacitor.loadAggregate(aggregateId, TestModel.class).assertLegal(command).apply(command, metadata);
        }

        @HandleCommand
        void handle(CreateModelInTwoSteps command) {
            FluxCapacitor.sendAndForgetCommand(new CreateModel());
        }

        @HandleQuery
        TestModel handle(GetModel query) {
            return FluxCapacitor.loadAggregate(aggregateId, TestModel.class).get();
        }

    }

    @Aggregate
    @Value
    public static class TestModel {
        List<Metadata> eventMetadata;

        @ApplyEvent
        public static TestModel handle(CreateModel event, Metadata metadata) {
            return new TestModel(new ArrayList<>(singletonList(metadata)));
        }


    }

    @Value
    private static class CreateModel {
    }

    @Value
    private static class CreateModelInTwoSteps {
    }


    @Value
    private static class GetModel {
    }

}
