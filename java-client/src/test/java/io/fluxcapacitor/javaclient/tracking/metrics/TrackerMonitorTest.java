package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.Consumer;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

class TrackerMonitorTest {
    final TestFixture testFixture = TestFixture.createAsync(new Handler());

    @Test
    void processBatchMetricPublished() {
        testFixture.whenEvent("test").expectMetrics((Predicate<ProcessBatchEvent>) e ->
                e.getConsumer().equals("custom-consumer") && e.getBatchSize() == 1);
    }

    @Consumer(name = "custom-consumer")
    static class Handler {
        @HandleEvent
        void handle(String ignored) {
        }
    }
}