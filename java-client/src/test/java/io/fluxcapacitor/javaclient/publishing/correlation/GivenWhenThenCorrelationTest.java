package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.ApplyEvent;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourced;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleMetrics;
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
    void testCorrelationDataIsSetOnDispatch() {
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
    void testConsumerIsSetForMetrics() {
        testFixture.givenCommands(new TriggerMetric())
                .whenQuery(new GetModel())
                .expectResult((Predicate<TestModel>) r -> r.getEventMetadata().size() == 1 &&
                        ofNullable(r.getEventMetadata().get(0))
                                .map(m -> m.get("$consumer").equals(m.get("$clientName") + "_COMMAND")
                                        && m.get("$trigger").equals(TriggerMetric.class.getName())
                                ).orElse(false));
    }

    private static class Handler {
        @HandleCommand
        void handle(Object command, Metadata metadata) {
            FluxCapacitor.loadAggregate(aggregateId, TestModel.class).assertLegal(command).apply(command, metadata);
        }

        @HandleQuery
        TestModel handle(GetModel query) {
            return FluxCapacitor.loadAggregate(aggregateId, TestModel.class).get();
        }

        @HandleCommand
        void handle(TriggerMetric command, Metadata metadata) {
            FluxCapacitor.publishMetrics(new TriggerMetric());
        }

        @HandleMetrics
        void handleMetrics(TriggerMetric command, Metadata metadata) {
            FluxCapacitor.sendAndForgetCommand(new SaveMetric(metadata));
        }
    }

    @EventSourced
    @Value
    public static class TestModel {
        List<Metadata> eventMetadata;

        @ApplyEvent
        public static TestModel handle(CreateModel event, Metadata metadata) {
            return new TestModel(new ArrayList<>(singletonList(metadata)));
        }

        @ApplyEvent
        public static TestModel handle(SaveMetric event, Metadata metadata) {
            return new TestModel(new ArrayList<>(singletonList(event.getMetadata())));
        }

    }

    @Value
    private static class CreateModel {
    }


    @Value
    private static class GetModel {
    }

    @Value
    private static class TriggerMetric {
    }

    @Value
    private static class SaveMetric {
        Metadata metadata;
    }

}
