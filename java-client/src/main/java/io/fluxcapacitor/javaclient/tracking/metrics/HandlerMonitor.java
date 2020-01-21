package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Slf4j
public class HandlerMonitor implements HandlerInterceptor {
    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    Handler<DeserializingMessage> handler,
                                                                    String consumer) {
        return message -> {
            Instant start = Instant.now();
            try {
                Object result = function.apply(message);
                publishMetrics(handler, consumer, message, false, start, result);
                return result;
            } catch (Throwable e) {
                publishMetrics(handler, consumer, message, true, start, e);
                throw e;
            }
        };
    }

    protected void publishMetrics(Handler<DeserializingMessage> handler, String consumer, DeserializingMessage message,
                                  boolean exceptionalResult, Instant start, Object result) {
        try {
            boolean completed = !(result instanceof CompletableFuture<?>) || ((CompletableFuture<?>) result).isDone();
            FluxCapacitor.publishMetrics(new HandleMessageEvent(
                    FluxCapacitor.get().client().name(), FluxCapacitor.get().client().id(), consumer,
                    handler.getTarget().getClass().getSimpleName(), message.getSerializedObject().getIndex(),
                    message.getPayloadClass().getSimpleName(), exceptionalResult,
                    start.until(Instant.now(), ChronoUnit.NANOS), completed));
            if (!completed) {
                ((CompletionStage<?>) result).whenComplete((r, e) -> {
                    DeserializingMessage current = DeserializingMessage.getCurrent();
                    try {
                        DeserializingMessage.setCurrent(message);
                        FluxCapacitor.publishMetrics(
                                new CompleteMessageEvent(
                                        FluxCapacitor.get().client().name(), FluxCapacitor.get().client().id(), consumer,
                                        handler.getTarget().getClass().getSimpleName(),
                                        message.getSerializedObject().getIndex(),
                                        message.getPayloadClass().getSimpleName(), e != null,
                                        start.until(Instant.now(), ChronoUnit.NANOS)));
                    } finally {
                        DeserializingMessage.setCurrent(current);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Failed to publish handler metrics", e);
        }
    }
}
