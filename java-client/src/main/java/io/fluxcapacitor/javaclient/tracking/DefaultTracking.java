package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.eventsourcing.CacheInvalidatingInterceptor;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingUtils;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.handling.HandlerInspector.createHandlers;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;

@AllArgsConstructor
@Slf4j
public class DefaultTracking implements Tracking {

    private final MessageType messageType;
    private final Class<? extends Annotation> handlerAnnotation;
    private final TrackingClient trackingClient;
    private final ResultGateway resultGateway;
    private final ErrorGateway errorGateway;
    private final List<ConsumerConfiguration> configurations;
    private final Serializer serializer;
    private final HandlerInterceptor handlerInterceptor;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;
    private final Set<ConsumerConfiguration> startedConfigurations = new HashSet<>();

    @Override
    public Registration start(FluxCapacitor fluxCapacitor, List<?> handlers) {
        synchronized (this) {
            Map<ConsumerConfiguration, List<Object>> consumers = handlers.stream()
                    .filter(h -> HandlerInspector.hasHandlerMethods(h.getClass(), handlerAnnotation))
                    .collect(groupingBy(h -> configurations.stream()
                            .filter(config -> config.getHandlerFilter().test(h)).findFirst()
                            .orElseThrow(() -> new TrackingException(format("Failed to find consumer for %s", h)))));
            if (!Collections.disjoint(consumers.keySet(), startedConfigurations)) {
                throw new TrackingException("Failed to start tracking. "
                                                    + "Consumers for some handlers have already started tracking.");
            }
            startedConfigurations.addAll(consumers.keySet());
            return consumers.entrySet().stream().map(e -> startTracking(e.getKey(), e.getValue(), fluxCapacitor))
                    .reduce(Registration::merge).orElse(Registration.noOp());
        }
    }

    protected Registration startTracking(ConsumerConfiguration configuration, List<Object> handlers,
                                         FluxCapacitor fluxCapacitor) {
        Consumer<List<SerializedMessage>> consumer = createConsumer(configuration, handlers);
        List<BatchInterceptor> batchInterceptors = new ArrayList<>(
                Arrays.asList(new FluxCapacitorInterceptor(fluxCapacitor),
                              new CacheInvalidatingInterceptor(fluxCapacitor.eventSourcing())));
        batchInterceptors.addAll(configuration.getTrackingConfiguration().getBatchInterceptors());
        TrackingConfiguration config = configuration.getTrackingConfiguration().toBuilder()
                .clearBatchInterceptors().batchInterceptors(batchInterceptors).build();
        String trackerName = configuration.prependApplicationName() 
                ? format("%s_%s", fluxCapacitor.client().name(), configuration.getName()) 
                : configuration.getName();
        return TrackingUtils.start(trackerName, consumer, trackingClient, config);
    }

    protected Consumer<List<SerializedMessage>> createConsumer(ConsumerConfiguration configuration,
                                                               List<Object> targets) {
        List<Handler<DeserializingMessage>> handlers = createHandlers(targets, handlerAnnotation, parameterResolvers);
        return serializedMessages -> {
            Stream<DeserializingMessage> messages =
                    serializer.deserialize(serializedMessages.stream(), false)
                            .map(m -> new DeserializingMessage(m, messageType));
            messages.forEach(m -> {
                try {
                    DeserializingMessage.setCurrent(m);
                    handlers.forEach(h -> tryHandle(m, h, configuration));
                } finally {
                    DeserializingMessage.removeCurrent();
                }
            });
        };
    }

    @SneakyThrows
    protected void tryHandle(DeserializingMessage message, Handler<DeserializingMessage> handler,
                             ConsumerConfiguration config) {
        if (handler.canHandle(message)) {
            try {
                handle(message, handler, config);
            } catch (Exception e) {
                Message error = new Message(e, MessageType.ERROR);
                errorGateway.report(error, message.getSerializedObject().getSource());
                config.getErrorHandler()
                        .handleError(e, format("Handler %s failed to handle a %s", handler, message.getType()),
                                     () -> handle(message, handler, config));
            }
        }
    }

    @SneakyThrows
    protected void handle(DeserializingMessage message, Handler<DeserializingMessage> handler,
                          ConsumerConfiguration config) {
        Exception exception = null;
        Object result;
        try {
            result = handlerInterceptor.interceptHandling(m -> handler.invoke(message), handler, config.getName())
                    .apply(message);
        } catch (FunctionalException e) {
            result = e;
            exception = e;
        } catch (Exception e) {
            result = new TechnicalException(format("Handler %s failed to handle a %s", handler, message.getType()));
            exception = e;
        }
        SerializedMessage serializedMessage = message.getSerializedObject();
        if (serializedMessage.getRequestId() != null) {
            resultGateway.respond(result, serializedMessage.getSource(), serializedMessage.getRequestId());
        }
        if (exception != null) {
            throw exception;
        }
    }

}
