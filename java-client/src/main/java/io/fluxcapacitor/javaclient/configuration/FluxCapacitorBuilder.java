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

public interface FluxCapacitorBuilder {
    DefaultFluxCapacitor.Builder serializer(Serializer serializer);

    DefaultFluxCapacitor.Builder snapshotSerializer(Serializer serializer);

    DefaultFluxCapacitor.Builder configureDefaultConsumer(MessageType messageType,
                                                          UnaryOperator<ConsumerConfiguration> updateFunction);

    DefaultFluxCapacitor.Builder addConsumerConfiguration(MessageType messageType,
                                                          ConsumerConfiguration consumerConfiguration);

    DefaultFluxCapacitor.Builder addTrackingParameterResolver(
            ParameterResolver<DeserializingMessage> parameterResolver);

    DefaultFluxCapacitor.Builder addDispatchInterceptor(DispatchInterceptor interceptor, MessageType... forTypes);

    DefaultFluxCapacitor.Builder addHandlerInterceptor(HandlerInterceptor interceptor, MessageType... forTypes);

    DefaultFluxCapacitor.Builder addCorrelationDataProvider(CorrelationDataProvider dataProvider);

    DefaultFluxCapacitor.Builder changeMessageRoutingInterceptor(DispatchInterceptor messageRoutingInterceptor);

    DefaultFluxCapacitor.Builder disableMessageCorrelation();

    DefaultFluxCapacitor.Builder disableCommandValidation();

    DefaultFluxCapacitor.Builder changeCommandValidationInterceptor(HandlerInterceptor validationInterceptor);

    FluxCapacitor build(Client client);
}
