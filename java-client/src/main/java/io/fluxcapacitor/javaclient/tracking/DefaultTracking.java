package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.caching.CacheInvalidatingInterceptor;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingUtils;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerFactory;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.common.ClientUtils.waitForResults;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.defaultInvokerFactory;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.handleBatch;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@AllArgsConstructor
@Slf4j
public class DefaultTracking implements Tracking {
    private static final HandlerConfiguration<DeserializingMessage> trackingHandlerConfiguration =
            HandlerConfiguration.<DeserializingMessage>builder().handlerFilter(e -> !ClientUtils.isLocalHandlerMethod(e))
                    .invokerFactory(defaultInvokerFactory).build();

    private final MessageType messageType;
    private final TrackingClient trackingClient;
    private final ResultGateway resultGateway;
    private final List<ConsumerConfiguration> configurations;
    private final Serializer serializer;
    private final HandlerFactory handlerFactory;

    private final Set<ConsumerConfiguration> startedConfigurations = new HashSet<>();
    private final Collection<CompletableFuture<?>> outstandingRequests = new CopyOnWriteArrayList<>();
    private final AtomicReference<Registration> shutdownFunction = new AtomicReference<>(Registration.noOp());

    @Override
    @Synchronized
    public Registration start(FluxCapacitor fluxCapacitor, List<?> handlers) {
        return fluxCapacitor.execute(fc -> {
            Map<ConsumerConfiguration, List<Handler<DeserializingMessage>>> consumers = handlers.stream()
                    .collect(groupingBy(h -> configurations.stream()
                            .filter(config -> config.getHandlerFilter().test(h)).findFirst()
                            .orElseThrow(() -> new TrackingException(format("Failed to find consumer for %s", h)))))
                    .entrySet().stream().flatMap(e -> {
                        List<Handler<DeserializingMessage>> converted = e.getValue().stream().flatMap(
                                target -> handlerFactory.createHandler(
                                        target, e.getKey().getName(), trackingHandlerConfiguration).map(
                                        Stream::of).orElse(Stream.empty())).collect(toList());
                        return converted.isEmpty() ? Stream.empty() : Stream.of(new SimpleEntry<>(e.getKey(), converted));
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

    protected Registration startTracking(ConsumerConfiguration configuration, List<Handler<DeserializingMessage>> handlers,
                                         FluxCapacitor fluxCapacitor) {
        Consumer<List<SerializedMessage>> consumer = createConsumer(configuration, handlers);
        List<BatchInterceptor> batchInterceptors = new ArrayList<>(
                Collections.singletonList(new FluxCapacitorInterceptor(fluxCapacitor)));
        if (configuration.getMessageType() == MessageType.COMMAND) {
            batchInterceptors.add(new CacheInvalidatingInterceptor(fluxCapacitor.cache()));
        }
        batchInterceptors.addAll(configuration.getTrackingConfiguration().getBatchInterceptors());
        Supplier<String> trackerIdFactory = configuration.getTrackingConfiguration().getTrackerIdFactory();
        TrackingConfiguration config = configuration.getTrackingConfiguration().toBuilder()
                .clearBatchInterceptors().batchInterceptors(batchInterceptors)
                .trackerIdFactory(() -> format("%s_%s", fluxCapacitor.client().id(), trackerIdFactory.get()))
                .build();
        String trackerName = configuration.prependApplicationName()
                ? format("%s_%s", fluxCapacitor.client().name(), configuration.getName())
                : configuration.getName();
        return TrackingUtils.start(trackerName, consumer, trackingClient, config);
    }

    protected Consumer<List<SerializedMessage>> createConsumer(ConsumerConfiguration configuration,
                                                               List<Handler<DeserializingMessage>> handlers) {
        return serializedMessages ->
                handleBatch(serializer.deserializeMessages(serializedMessages.stream(), false, messageType))
                        .forEach(m -> handlers.forEach(h -> tryHandle(m, h, configuration)));
    }

    @SneakyThrows
    protected void tryHandle(DeserializingMessage message, Handler<DeserializingMessage> handler,
                             ConsumerConfiguration config) {
        try {
            if (handler.canHandle(message)) {
                handle(message, handler, config);
            }
        } catch (Exception e) {
            config.getErrorHandler()
                    .handleError(e, format("Handler %s failed to handle a %s", handler, message),
                                 () -> handle(message, handler, config));
        }
    }

    @SneakyThrows
    protected void handle(DeserializingMessage message, Handler<DeserializingMessage> handler,
                          ConsumerConfiguration config) {
        Exception exception = null;
        Object result;
        try {
            result = handler.invoke(message);
        } catch (FunctionalException e) {
            result = e;
            exception = e;
        } catch (Exception e) {
            result = new TechnicalException(format("Handler %s failed to handle a %s", handler, message), e);
            exception = e;
        }
        SerializedMessage serializedMessage = message.getSerializedObject();
        boolean shouldSendResponse = shouldSendResponse(handler, message);
        if (result instanceof CompletableFuture<?>) {
            CompletableFuture<?> future = ((CompletableFuture<?>) result).whenComplete((r, e) -> {
                Object asyncResult = e == null ? r : e instanceof FunctionalException ? e : new TechnicalException(
                        format("Handler %s failed to handle a %s", handler, message), e);
                message.run(m -> {
                    try {
                        if (shouldSendResponse) {
                            resultGateway.respond(
                                    asyncResult, serializedMessage.getSource(), serializedMessage.getRequestId());
                        }
                        if (e != null) {
                            config.getErrorHandler().handleError((Exception) e, format(
                                    "Handler %s failed to handle a %s", handler, message),
                                                                 () -> handle(message, handler, config));
                        }
                    } catch (Exception exc) {
                        log.warn("Did not stop consumer {} after async handler {} failed to handle a {}",
                                 config.getName(), handler, message, exc);
                    }
                });
            });
            outstandingRequests.add(future);
            future.whenComplete((r, e) -> outstandingRequests.remove(future));
        } else if (shouldSendResponse) {
            resultGateway.respond(result, serializedMessage.getSource(), serializedMessage.getRequestId());
        }

        if (exception != null) {
            throw exception;
        }
    }

    @SneakyThrows
    private boolean shouldSendResponse(Handler<DeserializingMessage> handler, DeserializingMessage message) {
        SerializedMessage serializedMessage = message.getSerializedObject();
        if (serializedMessage.getRequestId() == null) {
            return false;
        }
        return !handler.isPassive(message);
    }

    @Override
    @Synchronized
    public void close() {
        shutdownFunction.get().merge(() -> waitForResults(Duration.ofSeconds(2), outstandingRequests)).cancel();
    }
}
