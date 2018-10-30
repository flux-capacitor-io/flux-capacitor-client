package io.fluxcapacitor.javaclient.configuration;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.publishing.correlation.CorrelationDataProvider;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;

import java.util.function.UnaryOperator;

/**
 * Builder for a Flux Capacitor client instance.
 */
public interface FluxCapacitorBuilder {
    /**
     * Register a custom serializer. This serializer will also be used for aggregate snapshots unless a custom snapshot
     * serializer is registered using {@link #snapshotSerializer(Serializer)}.
     *
     * @param serializer the serializer to register
     * @return the modified builder
     */
    FluxCapacitorBuilder serializer(Serializer serializer);

    /**
     * 
     * @param serializer
     * @return
     */
    FluxCapacitorBuilder snapshotSerializer(Serializer serializer);

    FluxCapacitorBuilder configureDefaultConsumer(MessageType messageType, UnaryOperator<ConsumerConfiguration> updateFunction);

    FluxCapacitorBuilder addConsumerConfiguration(MessageType messageType, ConsumerConfiguration consumerConfiguration);

    FluxCapacitorBuilder addHandlerParameterResolver(ParameterResolver<DeserializingMessage> parameterResolver);

    FluxCapacitorBuilder addDispatchInterceptor(DispatchInterceptor interceptor, MessageType... forTypes);

    FluxCapacitorBuilder addHandlerInterceptor(HandlerInterceptor interceptor, MessageType... forTypes);

    FluxCapacitorBuilder addCorrelationDataProvider(CorrelationDataProvider dataProvider);

    FluxCapacitorBuilder changeMessageRoutingInterceptor(DispatchInterceptor messageRoutingInterceptor);

    FluxCapacitorBuilder disableMessageCorrelation();

    FluxCapacitorBuilder disablePayloadValidation();

    FluxCapacitorBuilder disableDataProtection();

    FluxCapacitorBuilder collectTrackingMetrics();

    FluxCapacitorBuilder collectApplicationMetrics();

    FluxCapacitorBuilder changeCommandValidationInterceptor(HandlerInterceptor validationInterceptor);

    FluxCapacitor build(Client client);
}
