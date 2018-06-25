package io.fluxcapacitor.javaclient.common.metrics;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandleMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

class ApplicationMonitorTest {

    private final MetricsHandler metricsHandler = new MetricsHandler();
    private final FluxCapacitor fluxCapacitor = DefaultFluxCapacitor.builder().build(InMemoryClient.newInstance());

    @BeforeEach
    void setUp() {
        fluxCapacitor.startTracking(metricsHandler);
    }

    @Test
    void ensureMetricsGetPublished() throws InterruptedException {
        Registration registration = ApplicationMonitor.start(fluxCapacitor, Duration.ofMillis(10));
        metricsHandler.countDownLatch.await();
        registration.cancel();
    }

    private static class MetricsHandler {
        private final CountDownLatch countDownLatch = new CountDownLatch(1);

        @HandleMetrics
        public void handle(ApplicationMetricsEvent metrics) {
            countDownLatch.countDown();
        }
    }

}