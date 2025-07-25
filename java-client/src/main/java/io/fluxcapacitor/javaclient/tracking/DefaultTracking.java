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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.tracking.client.DefaultTracker;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerFactory;
import io.fluxcapacitor.javaclient.tracking.handling.Invocation;
import io.fluxcapacitor.javaclient.tracking.handling.LocalHandler;
import io.fluxcapacitor.javaclient.web.WebRequest;
import lombok.AllArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.unwrapException;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getLocalHandlerAnnotation;
import static io.fluxcapacitor.javaclient.common.ClientUtils.waitForResults;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.handleBatch;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_HANDSHAKE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_MESSAGE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_OPEN;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Default implementation of the {@link Tracking} interface that coordinates message tracking for a specific
 * {@link MessageType}.
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Assigning handler objects to appropriate {@link ConsumerConfiguration}s based on declared filters</li>
 *   <li>Creating and managing {@link Tracker} instances for those consumers and their associated topics</li>
 *   <li>Ensuring messages are deserialized, dispatched, and (if applicable) responded to with proper error handling</li>
 *   <li>Invoking handlers using the provided {@link HandlerFactory} and {@link HandlerInvoker}</li>
 *   <li>Integrating with {@link ResultGateway} to send back command/query/web responses when needed</li>
 * </ul>
 *
 * <p>
 * Supports per-consumer batch interceptors and general batch processing logic, including:
 * <ul>
 *   <li>Functional and technical exception management with retry hooks</li>
 *   <li>Tracking exclusivity to prevent handlers from being assigned to multiple consumers simultaneously</li>
 *   <li>Internal shutdown coordination and pending message flushes via {@link #close()}</li>
 * </ul>
 *
 * <h2>Typical Usage</h2>
 * This class is used internally when starting a {@link FluxCapacitor#registerHandlers(List)} invocation
 * for a given {@link MessageType}, and typically shouldn't be used directly by application developers.
 *
 * @see Tracking
 * @see ConsumerConfiguration
 * @see Tracker
 * @see ResultGateway
 */
@AllArgsConstructor
@Slf4j
public class DefaultTracking implements Tracking {
    private final HandlerFilter handlerFilter = (t, m) -> getLocalHandlerAnnotation(t, m)
            .map(LocalHandler::allowExternalMessages).orElse(true);
    private final MessageType messageType;
    private final ResultGateway resultGateway;
    private final List<ConsumerConfiguration> configurations;
    private final List<? extends BatchInterceptor> generalBatchInterceptors;
    private final Serializer serializer;
    private final HandlerFactory handlerFactory;

    private final Set<ConsumerConfiguration> startedConfigurations = new HashSet<>();
    private final Collection<CompletableFuture<?>> outstandingRequests = new CopyOnWriteArrayList<>();
    private final AtomicReference<Registration> shutdownFunction = new AtomicReference<>(Registration.noOp());

    /**
     * Starts tracking by assigning the given handlers to configured consumers and creating topic-specific or shared
     * trackers.
     * <p>
     * Throws a {@link TrackingException} if handlers can't be matched to consumers or if a consumer has already been
     * started previously.
     *
     * @param fluxCapacitor the owning {@link FluxCapacitor} instance
     * @param handlers      the handler instances to assign and activate
     * @return a {@link Registration} that can be used to stop all created trackers
     * @throws TrackingException if no consumer is found for a handler or if tracking has already been started
     */
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
                            return handlerFactory.createHandler(target, handlerFilter,
                                                                e.getKey().getHandlerInterceptors()).stream();
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

    /**
     * Matches the given handlers to known {@link ConsumerConfiguration}s using handler filters and exclusivity rules.
     * <p>
     * Throws a {@link TrackingException} if:
     * <ul>
     *   <li>No consumer is found for a handler</li>
     *   <li>Conflicting consumers have been defined for the same handler</li>
     * </ul>
     */
    private Map<ConsumerConfiguration, List<Object>> assignHandlersToConsumers(List<?> handlers) {
        var unassignedHandlers = new ArrayList<Object>(handlers);
        var configurations = Stream.concat(
                        ConsumerConfiguration.configurations(handlers.stream().map(ReflectionUtils::asClass).collect(toList())),
                        this.configurations.stream())
                .sorted(Comparator.comparing(ConsumerConfiguration::exclusive))
                .map(config -> config.toBuilder().batchInterceptors(generalBatchInterceptors).build())
                .collect(toMap(ConsumerConfiguration::getName, Function.identity(), (a, b) -> {
                    if (a.equals(b)) {
                        return a.toBuilder().handlerFilter(a.getHandlerFilter().or(b.getHandlerFilter())).build();
                    }
                    throw new IllegalStateException(format("Consumer name %s is already in use", a.getName()));
                }, LinkedHashMap::new));
        var result = configurations.values().stream().map(config -> {
            var matches =
                    unassignedHandlers.stream().filter(h -> config.getHandlerFilter().test(h)).toList();
            if (config.exclusive()) {
                unassignedHandlers.removeAll(matches);
            }
            return Map.entry(config, matches);
        }).collect(toMap(Entry::getKey, Entry::getValue));
        unassignedHandlers.removeAll(
                result.values().stream().flatMap(Collection::stream).distinct().toList());
        unassignedHandlers.forEach(h -> {
            throw new TrackingException(format("Failed to find consumer for %s", h));
        });
        return result;
    }

    protected Registration startTracking(ConsumerConfiguration configuration,
                                         List<Handler<DeserializingMessage>> handlers, FluxCapacitor fluxCapacitor) {
        var topics = ClientUtils.getTopics(messageType, handlers.stream().<Class<?>>map(Handler::getTargetClass)
                .filter(Objects::nonNull).toList());
        if (topics.isEmpty()) {
            return switch (messageType) {
                case DOCUMENT, CUSTOM -> Registration.noOp();
                default -> DefaultTracker.start(
                        createConsumer(configuration, handlers), messageType, configuration, fluxCapacitor);
            };
        }
        return topics.stream().map(topic -> DefaultTracker.start(
                        createConsumer(configuration, handlers), messageType, topic, configuration, fluxCapacitor))
                .reduce(Registration::merge).orElseGet(Registration::noOp);
    }

    protected Consumer<List<SerializedMessage>> createConsumer(ConsumerConfiguration config,
                                                               List<Handler<DeserializingMessage>> handlers) {
        return serializedMessages -> {
            String topic = Tracker.current().orElseThrow().getTopic();
            try {
                handleBatch(serializer.deserializeMessages(serializedMessages.stream(), messageType, topic))
                        .forEach(m -> handlers.forEach(h -> tryHandle(m, h, config, true)));
            } catch (BatchProcessingException e) {
                throw e;
            } catch (Throwable e) {
                config.getErrorHandler().handleError(
                        e, format("Failed to handle batch of consumer %s", config.getName()),
                        () -> handleBatch(
                                serializer.deserializeMessages(serializedMessages.stream(), messageType, topic))
                                .forEach(m -> handlers.forEach(h -> tryHandle(m, h, config, false))));
            }
        };
    }

    protected void tryHandle(DeserializingMessage message, Handler<DeserializingMessage> handler,
                             ConsumerConfiguration config, boolean reportResult) {
        getInvoker(message, handler, config).ifPresent(h -> {
            Object result;
            try {
                result = handle(message, h, handler, config);
            } catch (Throwable e) {
                try {
                    stopTracker(message, handler, e);
                    return;
                } finally {
                    if (reportResult) {
                        reportResult(e, h, message, config);
                    }
                }
            }
            try {
                if (reportResult) {
                    reportResult(result, h, message, config);
                }
            } catch (Throwable e) {
                stopTracker(message, handler, e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    protected Optional<HandlerInvoker> getInvoker(DeserializingMessage message, Handler<DeserializingMessage> handler,
                                                  ConsumerConfiguration config) {
        try {
            return handler.getInvoker(message);
        } catch (Throwable e) {
            try {
                Object retryResult = config.getErrorHandler().handleError(
                        e, format("Failed to check if handler %s is able to handle %s", handler, message),
                        () -> handler.getInvoker(message));
                return retryResult instanceof Optional<?> ? (Optional<HandlerInvoker>) retryResult : Optional.empty();
            } catch (Throwable e2) {
                stopTracker(message, handler, e2);
                return Optional.empty();
            }
        }
    }

    @SuppressWarnings("unchecked")
    protected Object handle(DeserializingMessage message, HandlerInvoker h, Handler<DeserializingMessage> handler,
                            ConsumerConfiguration config) {
        try {
            Object result = Invocation.performInvocation(h::invoke);
            return result instanceof CompletionStage<?> ? ((CompletionStage<Object>) result)
                    .exceptionally(e -> message.apply(m -> processError(e, message, h, handler, config))) : result;
        } catch (Throwable e) {
            return processError(e, message, h, handler, config);
        }
    }

    protected Object processError(Throwable e, DeserializingMessage message, HandlerInvoker h,
                                  Handler<DeserializingMessage> handler, ConsumerConfiguration config) {
        return config.getErrorHandler().handleError(
                unwrapException(e), format("Handler %s failed to handle a %s", handler, message),
                () -> Invocation.performInvocation(h::invoke));
    }

    protected void reportResult(Object result, HandlerInvoker h, DeserializingMessage message,
                                ConsumerConfiguration config) {
        if (result instanceof CompletionStage<?> s) {
            s.whenComplete((r, e) -> {
                try {
                    message.run(m -> reportResult(Optional.<Object>ofNullable(e).orElse(r), h, message, config));
                } finally {
                    if (e != null) {
                        close();
                    }
                }
            });
        } else {
            if (shouldSendResponse(h, message, result, config)) {
                if (result instanceof Throwable) {
                    result = unwrapException((Throwable) result);
                    if (!(result instanceof FunctionalException)) {
                        result = new TechnicalException(format("Handler %s failed to handle a %s",
                                                               h.getMethod(), message), (Throwable) result);
                    }
                }
                SerializedMessage request = message.getSerializedObject();
                try {
                    resultGateway.respond(result, request.getSource(), request.getRequestId());
                } catch (Throwable e) {
                    Object response = result;
                    config.getErrorHandler().handleError(
                            e, format("Failed to send result of a %s from handler %s", message, h.getMethod()),
                            () -> resultGateway.respond(response, request.getSource(), request.getRequestId()));
                }
            }
        }
    }

    protected boolean shouldSendResponse(HandlerInvoker invoker, DeserializingMessage request,
                                         Object result, ConsumerConfiguration config) {
        if (!request.getMessageType().isRequest() || config.passive() || invoker.isPassive()) {
            return false;
        }
        if (request.getMessageType() == MessageType.WEBREQUEST) {
            switch (WebRequest.getMethod(request.getMetadata())) {
                case WS_HANDSHAKE, WS_OPEN, WS_MESSAGE -> {
                    return true;
                }
            }
        }
        return request.getSerializedObject().getRequestId() != null;
    }

    protected void stopTracker(DeserializingMessage message, Handler<DeserializingMessage> handler, Throwable e) {
        throw e instanceof BatchProcessingException
                ? new BatchProcessingException(format("Handler %s failed to handle a %s", handler, message),
                                               e.getCause(), ((BatchProcessingException) e).getMessageIndex())
                : new BatchProcessingException(message.getIndex());
    }

    /**
     * Shuts down all started trackers and waits briefly for asynchronous results (e.g. command responses) to complete.
     */
    @Override
    @Synchronized
    public void close() {
        shutdownFunction.get().merge(() -> waitForResults(Duration.ofSeconds(2), outstandingRequests)).cancel();
    }
}
