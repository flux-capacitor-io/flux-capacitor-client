package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

import static io.fluxcapacitor.javaclient.FluxCapacitor.publishMetrics;

@Slf4j
public class TrackerMonitor implements BatchInterceptor {
    @Override
    public Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
        return batch -> {
            if (batch.isEmpty()) {
                consumer.accept(batch);
                return;
            }
            Instant start = Instant.now();
            consumer.accept(batch);
            long nsDuration = start.until(Instant.now(), ChronoUnit.NANOS);
            try {
                publishMetrics(new ProcessBatchEvent(
                        FluxCapacitor.get().client().name(), FluxCapacitor.get().client().id(), tracker.getName(),
                        tracker.getTrackerId(), batch.getLastIndex(), batch.getSize(), nsDuration));
            } catch (Exception e) {
                log.error("Failed to publish consumer metrics", e);
            }
        };
    }
}
