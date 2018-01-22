package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.HandlerException;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.gateway.ResultGateway;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Slf4j
public class DefaultTracking implements Tracking {

    private final Class<? extends Annotation> handlerAnnotation;
    private final TrackingClient trackingClient;
    private final ResultGateway resultGateway;
    private final List<ConsumerConfiguration> configurations;
    private final Serializer serializer;
    private final List<ParameterResolver<Message>> parameterResolvers;
    private final Set<ConsumerConfiguration> startedConfigurations = new HashSet<>();

    @Override
    public Registration start(List<Object> handlers, FluxCapacitor fluxCapacitor) {
        synchronized (this) {
            Map<ConsumerConfiguration, List<Object>> consumers = handlers.stream()
                    .filter(h -> HandlerInspector.hasHandlerMethods(h, handlerAnnotation))
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
        TrackingConfiguration config = configuration.getTrackingConfiguration().toBuilder().batchInterceptor(
                f -> {
                    FluxCapacitor.instance.set(fluxCapacitor);
                    return f.andThen(v -> {
                        FluxCapacitor.instance.remove();
                        return v;
                    });
                }).build();
        return TrackingUtils.start(configuration.getName(), consumer, trackingClient, config);
    }

    protected Consumer<List<SerializedMessage>> createConsumer(ConsumerConfiguration configuration,
                                                               List<Object> handlers) {
        List<HandlerInvoker<Message>> invokers = handlers.stream()
                .map(h -> HandlerInspector.inspect(h, handlerAnnotation, parameterResolvers)).collect(toList());
        return serializedMessages -> serializedMessages.forEach(s -> {
            Message message;
            try {
                message = new Message(serializer.deserialize(s.getData()), s.getMetadata());
            } catch (SerializationException e) {
                log.error("Not handling message because of a serialization exception", e);
                return;
            }
            invokers.stream().filter(i -> i.canHandle(message)).forEach(i -> {
                try {
                    handleResult(i.invoke(message), s);
                } catch (HandlerException e) {
                    handleResult(e.getCause(), s);
                } catch (Exception e) {
                    handleResult(e, s);
                }
            });
        });
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
