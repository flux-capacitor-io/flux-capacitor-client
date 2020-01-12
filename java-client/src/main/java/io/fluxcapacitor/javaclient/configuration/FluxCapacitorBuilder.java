package io.fluxcapacitor.javaclient.configuration;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;

import java.util.function.UnaryOperator;

/**
 * Builder for a Flux Capacitor client instance.
 */
public interface FluxCapacitorBuilder {
    FluxCapacitorBuilder configureDefaultConsumer(MessageType messageType,
                                                  UnaryOperator<ConsumerConfiguration> updateFunction);

    FluxCapacitorBuilder addConsumerConfiguration(MessageType messageType, ConsumerConfiguration consumerConfiguration);

    FluxCapacitorBuilder addDispatchInterceptor(DispatchInterceptor interceptor, MessageType... forTypes);

    FluxCapacitorBuilder addHandlerInterceptor(HandlerInterceptor interceptor, MessageType... forTypes);

    FluxCapacitorBuilder replaceMessageRoutingInterceptor(DispatchInterceptor messageRoutingInterceptor);

    FluxCapacitorBuilder addParameterResolver(ParameterResolver<DeserializingMessage> parameterResolver);

    /**
     * Register a custom serializer. This serializer will also be used for aggregate snapshots unless a custom snapshot
     * serializer is registered using {@link #replaceSnapshotSerializer(Serializer)}.
     */
    FluxCapacitorBuilder replaceSerializer(Serializer serializer);

    FluxCapacitorBuilder replaceSnapshotSerializer(Serializer serializer);

    FluxCapacitorBuilder disableErrorReporting();

    FluxCapacitorBuilder disableShutdownHook();

    FluxCapacitorBuilder disableMessageCorrelation();

    FluxCapacitorBuilder disablePayloadValidation();

    FluxCapacitorBuilder disableDataProtection();

    FluxCapacitorBuilder enableTrackingMetrics();

    FluxCapacitor build(Client client);
}
