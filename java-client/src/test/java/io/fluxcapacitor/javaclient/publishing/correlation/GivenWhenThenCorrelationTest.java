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

import java.util.function.Predicate;

import static java.lang.Long.parseLong;

public class GivenWhenThenCorrelationTest {
    private static final String aggregateId = "test";
    private final TestFixture testFixture = TestFixture.createAsync(new Handler());

    @Test
    void testDefaultCorrelationData() {
        testFixture.whenCommand(new CreateModel())
                .expectEvents((Predicate<Message>) e -> {
                    Metadata m = e.getMetadata();
                    return m.get("$consumer").equals(m.get("$clientName") + "_COMMAND")
                           && parseLong(m.get("$correlationId")) == parseLong(m.get("$traceId"))
                           && m.get("$trigger").equals(CreateModel.class.getName());
                });
    }

    @Test
    void testDefaultCorrelationDataAfterTwoSteps() {
        testFixture.whenCommand(new CreateModelInTwoSteps())
                .expectEvents((Predicate<Message>) e -> {
                    Metadata m = e.getMetadata();
                    return m.get("$consumer").equals(m.get("$clientName") + "_COMMAND")
                           && parseLong(m.get("$correlationId")) > parseLong(m.get("$traceId"))
                           && m.get("$trigger").equals(CreateModel.class.getName());
                });
    }

    @Test
    void testCustomTrace() {
        testFixture.whenCommand(new Message(new CreateModel(), Metadata.empty().withTrace("userName", "myself")))
                .expectEvents((Predicate<Message>) e -> e.getMetadata().get("$trace.userName").equals("myself"));
    }

    @Test
    void testCustomTraceInTwoSteps() {
        testFixture.whenCommand(new Message(new CreateModelInTwoSteps(), Metadata.empty().withTrace("userName", "myself")))
                .expectEvents((Predicate<Message>) e -> e.getMetadata().get("$trace.userName").equals("myself"));
    }

    private static class Handler {
        @HandleCommand
        void handle(Object command) {
            FluxCapacitor.loadAggregate(aggregateId, TestModel.class).assertLegal(command).apply(command);
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
        @ApplyEvent
        public static TestModel handle(CreateModel event) {
            return new TestModel();
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
