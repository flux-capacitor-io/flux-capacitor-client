package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerException;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingUtils;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;

@AllArgsConstructor
@Slf4j
public class DefaultTracking implements Tracking {

    private final Class<? extends Annotation> handlerAnnotation;
    private final TrackingClient trackingClient;
    private final ResultGateway resultGateway;
    private final List<ConsumerConfiguration> configurations;
    private final Serializer serializer;
    private final HandlerInterceptor handlerInterceptor;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;
    private final Set<ConsumerConfiguration> startedConfigurations = new HashSet<>();

    @Override
    public Registration start(List<Object> handlers, FluxCapacitor fluxCapacitor) {
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
                    .reduce(Registration::merge).orElse(() -> true);
        }
    }

    protected Registration startTracking(ConsumerConfiguration configuration, List<Object> handlers,
                                         FluxCapacitor fluxCapacitor) {
        Consumer<List<SerializedMessage>> consumer = createConsumer(configuration, handlers);
        TrackingConfiguration config = configuration.getTrackingConfiguration().toBuilder().batchMessageBatchInterceptor(
                c -> {
                    FluxCapacitor.instance.set(fluxCapacitor);
                    return messages -> {
                        try {
                            c.accept(messages);
                        } finally {
                            FluxCapacitor.instance.remove();
                        }
                    };
                }).build();
        return TrackingUtils.start(configuration.getName(), consumer, trackingClient, config);
    }

    protected Consumer<List<SerializedMessage>> createConsumer(ConsumerConfiguration configuration,
                                                               List<Object> handlers) {
        List<Handler<DeserializingMessage>> invokers
                = HandlerInspector.createHandlers(handlers, handlerAnnotation, parameterResolvers);
        return serializedMessages -> {
            Stream<DeserializingMessage> messages =
                    serializer.deserialize(serializedMessages.stream(), false).map(DeserializingMessage::new);
            messages.forEach(m -> invokers.forEach(i -> handle(m, i)));
        };
    }

    protected void handle(DeserializingMessage message, Handler<DeserializingMessage> handler) {
        if (handler.canHandle(message)) {
            try {
                handleResult(handlerInterceptor.interceptHandling(m -> handler.invoke(message)).apply(message),
                             message.getSerializedObject());
            } catch (HandlerException e) {
                handleResult(e.getCause(), message.getSerializedObject());
            } catch (Exception e) {
                handleResult(e, message.getSerializedObject());
            }
        }
    }

    protected void handleResult(Object result, SerializedMessage message) {
        if (message.getRequestId() != null) {
            resultGateway.respond(result, message.getSource(), message.getRequestId());
        } else if (result instanceof Exception) {
            log.error(format("Failed to handle a message with index %s. Continuing processing with next handler.",
                             message.getIndex()), (Exception) result);
        }
    }

}
