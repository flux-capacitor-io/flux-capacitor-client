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

import java.util.function.Function;
import java.util.function.UnaryOperator;

public interface FluxCapacitorBuilder {
    FluxCapacitorBuilder serializer(Serializer serializer);

    FluxCapacitorBuilder snapshotSerializer(Serializer serializer);

    FluxCapacitorBuilder configureDefaultConsumer(MessageType messageType, UnaryOperator<ConsumerConfiguration> updateFunction);

    FluxCapacitorBuilder addConsumerConfiguration(MessageType messageType, ConsumerConfiguration consumerConfiguration);

    FluxCapacitorBuilder addTrackingParameterResolver(ParameterResolver<DeserializingMessage> parameterResolver);

    FluxCapacitorBuilder addDispatchInterceptor(DispatchInterceptor interceptor, MessageType... forTypes);

    FluxCapacitorBuilder addHandlerInterceptor(HandlerInterceptor interceptor, MessageType... forTypes);

    FluxCapacitorBuilder addCorrelationDataProvider(CorrelationDataProvider dataProvider);

    FluxCapacitorBuilder changeMessageRoutingInterceptor(DispatchInterceptor messageRoutingInterceptor);

    FluxCapacitorBuilder disableMessageCorrelation();

    FluxCapacitorBuilder disableCommandValidation();

    FluxCapacitorBuilder collectTrackingMetrics();

    FluxCapacitorBuilder collectApplicationMetrics();

    FluxCapacitorBuilder changeCommandValidationInterceptor(HandlerInterceptor validationInterceptor);
    
    default FluxCapacitorBuilder registerLocalHandlers(Object... handlers) {
        FluxCapacitorBuilder result = this;
        for (Object handler : handlers) {
            result = result.registerLocalHandler(fc -> handler);
        }
        return result;
    }

    FluxCapacitorBuilder registerLocalHandler(Function<FluxCapacitor, Object> handlerFactory);

    FluxCapacitor build(Client client);
}
