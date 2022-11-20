/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.Invocation;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.tracking.client.DefaultTracker;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerFactory;
import lombok.AllArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.asInstance;
import static io.fluxcapacitor.javaclient.common.ClientUtils.waitForResults;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.handleBatch;
import static io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration.handlerConfigurations;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@AllArgsConstructor
@Slf4j
public class DefaultTracking implements Tracking {
    private final HandlerFilter handlerFilter = ClientUtils::isTrackingHandler;
    private final MessageType messageType;
    private final ResultGateway resultGateway;
    private final List<ConsumerConfiguration> configurations;
    private final List<? extends BatchInterceptor> generalBatchInterceptors;
    private final Serializer serializer;
    private final HandlerFactory handlerFactory;

    private final Set<ConsumerConfiguration> startedConfigurations = new HashSet<>();
    private final Collection<CompletableFuture<?>> outstandingRequests = new CopyOnWriteArrayList<>();
    private final AtomicReference<Registration> shutdownFunction = new AtomicReference<>(Registration.noOp());

    @SuppressWarnings("unchecked")
    @Override
    @Synchronized
    public Registration start(FluxCapacitor fluxCapacitor, List<?> handlers) {
        return fluxCapacitor.apply(fc -> {
            Map<ConsumerConfiguration, List<Handler<DeserializingMessage>>> consumers =
                    assignHandlersToConsumers(handlers).entrySet().stream().flatMap(e -> {
                        List<Handler<DeserializingMessage>> converted = e.getValue().stream().flatMap(target -> {
                            if (target instanceof Handler<?>) {
                                return Stream.of((Handler<DeserializingMessage>) target);
                            }
                            return handlerFactory.createHandler(asInstance(target), e.getKey().getName(), handlerFilter)
                                    .stream();
                        }).collect(toList());
                        return converted.isEmpty() ? Stream.empty() :
                                Stream.of(new SimpleEntry<>(e.getKey(), converted));
                    }).collect(toMap(Entry::getKey, Entry::getValue));


            if (!Collections.disjoint(consumers.keySet(), startedConfigurations)) {
                throw new TrackingException("Failed to start tracking. "
                                            + "Consumers for some handlers have already started tracking.");
            }

            startedConfigurations.addAll(consumers.keySet());
            Registration registration =
                    consumers.entrySet().stream().map(e -> startTracking(e.getKey(), e.getValue(), fc))
                            .reduce(Registration::merge).orElse(Registration.noOp());
            shutdownFunction.updateAndGet(r -> r.merge(registration));
            return registration;
        });
    }

    private Map<ConsumerConfiguration, List<Object>> assignHandlersToConsumers(List<?> handlers) {
        var unassignedHandlers = new ArrayList<Object>(handlers);
        var configurations = Stream.concat(
                        handlers.stream().flatMap(
                                h -> handlerConfigurations(h.getClass()).filter(c -> c.getMessageType() == messageType)),
                        this.configurations.stream())
                .map(config -> config.toBuilder().batchInterceptors(generalBatchInterceptors).build())
                .collect(toMap(ConsumerConfiguration::getName, Function.identity(), (a, b) -> {
                    if (a.equals(b)) {
                        return a.toBuilder().handlerFilter(a.getHandlerFilter().or(b.getHandlerFilter())).build();
                    }
                    throw new IllegalStateException(format("Consumer name %s is already in use", a.getName()));
                }, LinkedHashMap::new));
        var result = configurations.values().stream().map(config -> {
            var matches =
                    unassignedHandlers.stream().filter(h -> config.getHandlerFilter().test(h)).collect(toList());
            if (config.exclusive()) {
                unassignedHandlers.removeAll(matches);
            }
            return Map.entry(config, matches);
        }).collect(toMap(Entry::getKey, Entry::getValue));
        unassignedHandlers.removeAll(
                result.values().stream().flatMap(Collection::stream).distinct().collect(toList()));
        unassignedHandlers.forEach(h -> {
            throw new TrackingException(format("Failed to find consumer for %s", h));
        });
        return result;
    }

    protected Registration startTracking(ConsumerConfiguration configuration,
                                         List<Handler<DeserializingMessage>> handlers, FluxCapacitor fluxCapacitor) {
        return DefaultTracker.start(createConsumer(configuration, handlers), configuration, fluxCapacitor);
    }

    protected Consumer<List<SerializedMessage>> createConsumer(ConsumerConfiguration configuration,
                                                               List<Handler<DeserializingMessage>> handlers) {
        return serializedMessages ->
                handleBatch(serializer.deserializeMessages(serializedMessages.stream(), messageType))
                        .forEach(m -> handlers.forEach(h -> tryHandle(m, h, configuration)));
    }

    protected void tryHandle(DeserializingMessage message, Handler<DeserializingMessage> handler,
                             ConsumerConfiguration config) {
        handler.findInvoker(message).ifPresent(h -> {
            try {
                reportResult(handle(message, h, handler, config), h, message, config);
            } catch (Throwable e) {
                reportResult(e, h, message, config);
                throw e instanceof BatchProcessingException
                        ? new BatchProcessingException(format("Handler %s failed to handle a %s", handler, message),
                                                       e.getCause(), ((BatchProcessingException) e).getMessageIndex())
                        : new BatchProcessingException(message.getIndex());
            }
        });
    }

    @SuppressWarnings("unchecked")
    protected Object handle(DeserializingMessage message, HandlerInvoker h, Handler<DeserializingMessage> handler,
                            ConsumerConfiguration config) {
        try {
            Object result = Invocation.performInvocation(h::invoke);
            return result instanceof CompletableFuture ? ((CompletableFuture<Object>) result)
                    .exceptionally(e -> message.apply(m -> processError(e, message, h, handler, config))) : result;
        } catch (Throwable e) {
            return processError(e, message, h, handler, config);
        }
    }

    protected Object processError(Throwable e, DeserializingMessage message, HandlerInvoker h,
                                  Handler<DeserializingMessage> handler, ConsumerConfiguration config) {
        return config.getErrorHandler().handleError(e, format("Handler %s failed to handle a %s", handler, message),
                                                    () -> Invocation.performInvocation(h::invoke));
    }

    protected void reportResult(Object result, HandlerInvoker h, DeserializingMessage message,
                                ConsumerConfiguration config) {
        if (result instanceof CompletableFuture<?>) {
            ((CompletableFuture<?>) result).whenComplete((r, e) -> {
                try {
                    message.run(m -> reportResult(Optional.<Object>ofNullable(e).orElse(r), h, message, config));
                } finally {
                    if (e != null) {
                        close();
                    }
                }
            });
        } else {
            if (shouldSendResponse(h, message, config)) {
                if (result instanceof Throwable) {
                    result = ObjectUtils.unwrapException((Throwable) result);
                    if (!(result instanceof FunctionalException)) {
                        result = new TechnicalException(format("Handler %s failed to handle a %s",
                                                               h.getMethod(), message), (Throwable) result);
                    }
                }
                SerializedMessage serializedMessage = message.getSerializedObject();
                resultGateway.respond(result, serializedMessage.getSource(), serializedMessage.getRequestId());
            }
        }
    }

    protected boolean shouldSendResponse(HandlerInvoker invoker, DeserializingMessage message,
                                         ConsumerConfiguration config) {
        return message.getSerializedObject().getRequestId() != null && !config.passive() && !invoker.isPassive()
               && message.getMessageType() != MessageType.RESULT && message.getMessageType() != MessageType.WEBRESPONSE;
    }

    @Override
    @Synchronized
    public void close() {
        shutdownFunction.get().merge(() -> waitForResults(Duration.ofSeconds(2), outstandingRequests)).cancel();
    }
}
