package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Aggregate;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.ApplyEvent;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

public class GivenWhenThenCorrelationTest {
    private static final String aggregateId = "test";
    private final TestFixture testFixture = TestFixture.createAsync(new Handler());

    @Test
    void testDefaultCorrelationData() {
        testFixture.givenCommands(new CreateModel())
                .whenQuery(new GetModel())
                .expectResult((Predicate<TestModel>) r -> r.getEventMetadata().size() == 1 &&
                        ofNullable(r.getEventMetadata().get(0))
                                .map(m -> m.get("$consumer").equals(m.get("$clientName") + "_COMMAND")
                                        && m.get("$traceId").equals("0")
                                        && m.get("$correlationId").equals("0")
                                        && m.get("$trigger").equals(CreateModel.class.getName())
                                ).orElse(false));
    }

    @Test
    void testDefaultCorrelationDataAfterTwoSteps() {
        testFixture.givenCommands(new CreateModelInTwoSteps())
                .whenQuery(new GetModel())
                .expectResult((Predicate<TestModel>) r -> r.getEventMetadata().size() == 1 &&
                        ofNullable(r.getEventMetadata().get(0))
                                .map(m -> m.get("$consumer").equals(m.get("$clientName") + "_COMMAND")
                                        && m.get("$traceId").equals("0")
                                        && m.get("$correlationId").equals("1")
                                        && m.get("$trigger").equals(CreateModel.class.getName())
                                ).orElse(false));
    }

    @Test
    void testCustomTrace() {
        testFixture.givenCommands(new Message(new CreateModel(), Metadata.empty().withTrace("userName", "myself")))
                .whenQuery(new GetModel())
                .expectResult((Predicate<TestModel>) r -> r.getEventMetadata().size() == 1 &&
                        ofNullable(r.getEventMetadata().get(0))
                                .map(m -> m.get("$trace.userName").equals("myself")).orElse(false));
    }

    @Test
    void testCustomTraceInTwoSteps() {
        testFixture.givenCommands(new Message(new CreateModelInTwoSteps(), Metadata.empty().withTrace("userName", "myself")))
                .whenQuery(new GetModel())
                .expectResult((Predicate<TestModel>) r -> r.getEventMetadata().size() == 1 &&
                        ofNullable(r.getEventMetadata().get(0))
                                .map(m -> m.get("$trace.userName").equals("myself")).orElse(false));
    }

    private static class Handler {
        @HandleCommand
        void handle(Object command, Metadata metadata) {
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
