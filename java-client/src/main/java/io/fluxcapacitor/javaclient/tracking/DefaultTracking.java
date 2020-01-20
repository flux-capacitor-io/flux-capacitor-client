package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.eventsourcing.CacheInvalidatingInterceptor;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingUtils;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.handling.HandlerConfiguration.defaultHandlerConfiguration;
import static io.fluxcapacitor.common.handling.HandlerInspector.createHandler;
import static io.fluxcapacitor.common.handling.HandlerInspector.hasHandlerMethods;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.convert;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Slf4j
public class DefaultTracking implements Tracking {

    private final MessageType messageType;
    private final Class<? extends Annotation> handlerAnnotation;
    private final TrackingClient trackingClient;
    private final ResultGateway resultGateway;
    private final List<ConsumerConfiguration> configurations;
    private final Serializer serializer;
    private final HandlerInterceptor handlerInterceptor;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;
    private final Set<ConsumerConfiguration> startedConfigurations = new HashSet<>();
    private final AtomicReference<Registration> shutdownFunction = new AtomicReference<>(Registration.noOp());

    @Override
    @Synchronized
    public Registration start(FluxCapacitor fluxCapacitor, List<?> handlers) {
        Map<ConsumerConfiguration, List<Object>> consumers = handlers.stream()
                .filter(h -> hasHandlerMethods(h.getClass(), handlerAnnotation))
                .collect(groupingBy(h -> configurations.stream()
                        .filter(config -> config.getHandlerFilter().test(h)).findFirst()
                        .orElseThrow(() -> new TrackingException(format("Failed to find consumer for %s", h)))));
        if (!Collections.disjoint(consumers.keySet(), startedConfigurations)) {
            throw new TrackingException("Failed to start tracking. "
                                                + "Consumers for some handlers have already started tracking.");
        }
        startedConfigurations.addAll(consumers.keySet());
        Registration registration =
                consumers.entrySet().stream().map(e -> startTracking(e.getKey(), e.getValue(), fluxCapacitor))
                        .reduce(Registration::merge).orElse(Registration.noOp());
        shutdownFunction.updateAndGet(registration::merge);
        return registration;
    }

    protected Registration startTracking(ConsumerConfiguration configuration, List<Object> handlers,
                                         FluxCapacitor fluxCapacitor) {
        Consumer<List<SerializedMessage>> consumer = createConsumer(configuration, handlers);
        List<BatchInterceptor> batchInterceptors = new ArrayList<>(
                Collections.singletonList(new FluxCapacitorInterceptor(fluxCapacitor)));
        if (configuration.getMessageType() == MessageType.COMMAND) {
            batchInterceptors.add(new CacheInvalidatingInterceptor(fluxCapacitor.eventSourcing()));
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
                                                               List<Object> targets) {
        List<Handler<DeserializingMessage>> handlers = targets.stream()
                .filter(o -> hasHandlerMethods(o.getClass(), handlerAnnotation))
                .map(o -> createHandler(o, handlerAnnotation, parameterResolvers, defaultHandlerConfiguration()))
                .collect(toList());
        return serializedMessages -> {
            Stream<DeserializingMessage> messages = convert(
                    serializer.deserialize(serializedMessages.stream(), false), messageType);
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
            result = handlerInterceptor.interceptHandling(m -> handler.invoke(message), handler, config.getName())
                    .apply(message);
        } catch (FunctionalException e) {
            result = e;
            exception = e;
        } catch (Exception e) {
            result = new TechnicalException(format("Handler %s failed to handle a %s", handler, message));
            exception = e;
        }
        SerializedMessage serializedMessage = message.getSerializedObject();
        boolean shouldSendResponse = shouldSendResponse(handler, message);
        if (result instanceof CompletionStage<?>) {
            ((CompletionStage<?>) result).whenComplete((r, e) -> {
                Object asyncResult = r;
                if (e != null) {
                    if (!(e instanceof FunctionalException)) {
                        asyncResult = new TechnicalException(
                                format("Handler %s failed to handle a %s", handler, message));
                    }
                }
                try {
                    DeserializingMessage.setCurrent(message);
                    if (shouldSendResponse) {
                        resultGateway
                                .respond(asyncResult, serializedMessage.getSource(), serializedMessage.getRequestId());
                    }
                    if (e != null) {
                        config.getErrorHandler().handleError((Exception) e, format(
                                "Handler %s failed to handle a %s", handler, message),
                                                             () -> handle(message, handler, config));
                    }
                } catch (Exception exc) {
                    log.warn("Did not stop consumer {} after async handler {} failed to handle a {}",
                             config.getName(), handler, message, exc);
                } finally {
                    DeserializingMessage.removeCurrent();
                }
            });
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
        Executable method = handler.getMethod(message);
        Annotation annotation = method.getAnnotation(handlerAnnotation);
        Optional<Method> isPassive = Arrays.stream(handlerAnnotation.getMethods())
                .filter(m -> m.getName().equals("passive")).findFirst();
        if (isPassive.isPresent()) {
            return !((boolean) isPassive.get().invoke(annotation));
        }
        return true;
    }

    @Override
    @Synchronized
    public void close() {
        shutdownFunction.get().cancel();
    }
}
