package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;

@Slf4j
public class HandlerMonitor implements HandlerInterceptor {
    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    Handler<DeserializingMessage> handler, String consumer) {
        return message -> {
            Instant start = Instant.now();
            try {
                Object result = function.apply(message);
                publishMetrics(handler, consumer, message, false, start);
                return result;
            } catch (Throwable e) {
                publishMetrics(handler, consumer, message, true, start);
                throw e;
            }
        };
    }

    protected void publishMetrics(Object handler, String consumer, DeserializingMessage message,
                                  boolean exceptionalResult, Instant start) {
        try {
            long nsDuration = start.until(Instant.now(), ChronoUnit.NANOS);
            FluxCapacitor.publishMetrics(
                    new HandleMessageEvent(FluxCapacitor.get().client().name(), FluxCapacitor.get().client().id(),
                                           consumer, handler.getClass().getSimpleName(),
                                           message.getPayloadClass().getSimpleName(), exceptionalResult, nsDuration));
        } catch (Exception e) {
            log.error("Failed to publish handler metrics", e);
        }
    }
}
