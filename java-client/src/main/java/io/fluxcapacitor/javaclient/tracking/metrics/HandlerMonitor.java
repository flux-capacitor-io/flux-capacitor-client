/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSelf;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.LocalHandler;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.common.ClientUtils.getLocalHandlerAnnotation;
import static java.time.temporal.ChronoUnit.NANOS;

@Slf4j
public class HandlerMonitor implements HandlerInterceptor {
    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    HandlerInvoker invoker) {
        return message -> {
            Instant start = Instant.now();
            try {
                Object result = function.apply(message);
                publishMetrics(invoker, message, false, start, result);
                return result;
            } catch (Throwable e) {
                publishMetrics(invoker, message, true, start, e);
                throw e;
            }
        };
    }

    protected void publishMetrics(HandlerInvoker invoker, DeserializingMessage message,
                                  boolean exceptionalResult, Instant start, Object result) {
        try {
            if (logMetrics(invoker)) {
                String consumer = Tracker.current().map(Tracker::getName)
                        .orElseGet(() -> "local-" + message.getMessageType());
                boolean completed =
                        !(result instanceof CompletableFuture<?>) || ((CompletableFuture<?>) result).isDone();
                FluxCapacitor.getOptionally().ifPresent(fc -> fc.metricsGateway().publish(new HandleMessageEvent(
                        consumer, invoker.getTargetClass().getSimpleName(),
                        message.getIndex(),
                        message.getType(), exceptionalResult, start.until(Instant.now(), NANOS), completed)));
                if (!completed) {
                    Map<String, String> correlationData = FluxCapacitor.currentCorrelationData();
                    ((CompletionStage<?>) result).whenComplete((r, e) -> message.run(
                            m -> FluxCapacitor.getOptionally().ifPresent(fc -> fc.metricsGateway().publish(
                                    new CompleteMessageEvent(
                                            consumer, invoker.getTargetClass().getSimpleName(),
                                            m.getIndex(), m.getType(),
                                            e != null, start.until(Instant.now(), NANOS)),
                                    Metadata.of(correlationData)))));
                }
            }
        } catch (Exception e) {
            log.error("Failed to publish handler metrics", e);
        }
    }

    protected boolean logMetrics(HandlerInvoker invoker) {
        return getLocalHandlerAnnotation(invoker.getTargetClass(), invoker.getMethod())
                .map(LocalHandler::logMetrics)
                .orElseGet(() -> !(invoker.getMethodAnnotation() instanceof HandleSelf));
    }

}
