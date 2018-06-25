package io.fluxcapacitor.javaclient.common.metrics;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationMonitor {
    private final FluxCapacitor fluxCapacitor;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static Registration start(FluxCapacitor fluxCapacitor, Duration period) {
        ApplicationMonitor applicationMonitor = new ApplicationMonitor(fluxCapacitor);
        applicationMonitor.start(period);
        return applicationMonitor::stop;
    }

    protected void start(Duration period) {
        long delay = period.toMillis();
        scheduler.scheduleWithFixedDelay(this::registerMetrics, delay, delay, TimeUnit.MILLISECONDS);
    }

    protected void registerMetrics() {
        ApplicationMetricsEvent metrics;
        try {
            double cpu = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            long memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();
            double garbageCollectionTimeAverage = ManagementFactory.getGarbageCollectorMXBeans().stream()
                    .mapToDouble(bean -> bean.getCollectionCount() > 0 ?
                            (double) bean.getCollectionTime() / bean.getCollectionCount() : 0.0d)
                    .filter(avg -> avg > 0L).average().orElse(0);
            metrics = new ApplicationMetricsEvent(fluxCapacitor.client().name(), fluxCapacitor.client().id(),
                                                  cpu, memory, threadCount, garbageCollectionTimeAverage);
        } catch (Exception e) {
            log.error("Failed to collect application metrics. Stopping application monitor.", e);
            stop();
            throw e;
        }
        try {
            fluxCapacitor.metricsGateway().publish(metrics);
        } catch (Exception e) {
            log.error("Failed to publish application metrics", e);
        }
    }

    protected void stop() {
        scheduler.shutdownNow();
    }

}
