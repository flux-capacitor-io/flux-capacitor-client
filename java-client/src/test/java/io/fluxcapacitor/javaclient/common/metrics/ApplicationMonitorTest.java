package io.fluxcapacitor.javaclient.common.metrics;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandleMetrics;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

public class ApplicationMonitorTest {

    private final MetricsHandler metricsHandler = new MetricsHandler();
    private final FluxCapacitor fluxCapacitor = DefaultFluxCapacitor.builder().build(InMemoryClient.newInstance());

    @Before
    public void setUp() throws Exception {
        fluxCapacitor.startTracking(metricsHandler);
    }

    @Test
    public void ensureMetricsGetPublished() throws InterruptedException {
        Registration registration = ApplicationMonitor.start(fluxCapacitor, Duration.ofMillis(10));
        metricsHandler.countDownLatch.await();
        registration.cancel();
    }

    private static class MetricsHandler {
        private final CountDownLatch countDownLatch = new CountDownLatch(1);

        @HandleMetrics
        public void handle(ApplicationMetrics applicationMetrics) {
            countDownLatch.countDown();
        }
    }

}