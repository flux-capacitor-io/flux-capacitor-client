package io.fluxcapacitor.javaclient.configuration;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.eventsourcing.DefaultEventStore;
import io.fluxcapacitor.javaclient.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.gateway.*;
import io.fluxcapacitor.javaclient.keyvalue.DefaultKeyValueStore;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.scheduling.DefaultScheduler;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.DefaultTracking;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import io.fluxcapacitor.javaclient.tracking.TrackingException;
import io.fluxcapacitor.javaclient.tracking.handler.*;
import lombok.AllArgsConstructor;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.common.MessageType.*;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@AllArgsConstructor
public class DefaultFluxCapacitor implements FluxCapacitor {

    private final Map<MessageType, Tracking> trackingSupplier;
    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;
    private final ResultGateway resultGateway;
    private final EventStore eventStore;
    private final KeyValueStore keyValueStore;
    private final Scheduler scheduler;

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public EventStore eventStore() {
        return eventStore;
    }

    @Override
    public Scheduler scheduler() {
        return scheduler;
    }

    @Override
    public KeyValueStore keyValueStore() {
        return keyValueStore;
    }

    @Override
    public CommandGateway commandGateway() {
        return commandGateway;
    }

    @Override
    public QueryGateway queryGateway() {
        return queryGateway;
    }

    @Override
    public ResultGateway resultGateway() {
        return resultGateway;
    }

    @Override
    public Tracking tracking(MessageType messageType) {
        return Optional.ofNullable(trackingSupplier.get(messageType)).orElseThrow(
                () -> new TrackingException(String.format("Tracking is not supported for type %s", messageType)));
    }

    public static class Builder {

        private Serializer serializer = new JacksonSerializer();
        private final Map<MessageType, List<ConsumerConfiguration>> consumerConfigurations = defaultConfigurations();
        private final List<ParameterResolver<DeserializingMessage>> parameterResolvers = defaultParameterResolvers();

        protected List<ParameterResolver<DeserializingMessage>> defaultParameterResolvers() {
            return new ArrayList<>(Arrays.asList(new PayloadParameterResolver(), new MetadataParameterResolver()));
        }

        protected Map<MessageType, List<ConsumerConfiguration>> defaultConfigurations() {
            return unmodifiableMap(stream(MessageType.values()).collect(toMap(identity(), messageType ->
                    new ArrayList<>(singletonList(ConsumerConfiguration.getDefault(messageType))))));
        }

        public Builder serializer(Serializer serializer) {
            this.serializer = serializer;
            return this;
        }

        public Builder configureDefaultConsumer(MessageType messageType,
                                                UnaryOperator<ConsumerConfiguration> updateFunction) {
            List<ConsumerConfiguration> configurations = consumerConfigurations.get(messageType);
            ConsumerConfiguration defaultConfiguration = configurations.get(configurations.size() - 1);
            configurations.set(configurations.size() - 1, updateFunction.apply(defaultConfiguration));
            return this;
        }

        public Builder addConsumerConfiguration(MessageType messageType, ConsumerConfiguration consumerConfiguration) {
            List<ConsumerConfiguration> configurations = consumerConfigurations.get(messageType);
            configurations.add(configurations.size() - 1, consumerConfiguration);
            return this;
        }

        public Builder addParameterResolver(ParameterResolver<DeserializingMessage> parameterResolver) {
            parameterResolvers.add(parameterResolver);
            return this;
        }

        public FluxCapacitor build(FluxCapacitorClient client) {
            ResultGateway resultGateway =
                    new DefaultResultGateway(client.getGatewayClient(RESULT), serializer);
            Map<MessageType, Tracking> trackingMap = stream(MessageType.values())
                    .collect(toMap(identity(), m -> createTracking(m, client, resultGateway)));
            RequestHandler requestHandler =
                    new DefaultRequestHandler(client.getTrackingClient(RESULT), client.getProperties());
            CommandGateway commandGateway =
                    new DefaultCommandGateway(client.getGatewayClient(COMMAND), requestHandler, serializer);
            QueryGateway queryGateway =
                    new DefaultQueryGateway(client.getGatewayClient(QUERY), requestHandler, serializer);
            KeyValueStore keyValueStore = new DefaultKeyValueStore(client.getKeyValueClient(), serializer);
            EventStore eventStore = new DefaultEventStore(client.getEventStoreClient(), client.getGatewayClient(EVENT),
                                                          keyValueStore, serializer);
            Scheduler scheduler = new DefaultScheduler(client.getSchedulingClient(), serializer);
            return new DefaultFluxCapacitor(trackingMap, commandGateway, queryGateway, resultGateway, eventStore,
                                            keyValueStore, scheduler);
        }

        protected Tracking createTracking(MessageType messageType, FluxCapacitorClient client,
                                          ResultGateway resultGateway) {
            return new DefaultTracking(getHandlerAnnotation(messageType), client.getTrackingClient(messageType),
                                       resultGateway, consumerConfigurations.get(messageType), serializer,
                                       parameterResolvers);
        }

        protected Class<? extends Annotation> getHandlerAnnotation(MessageType messageType) {
            switch (messageType) {
                case COMMAND:
                    return HandleCommand.class;
                case EVENT:
                    return HandleEvent.class;
                case NOTIFICATION:
                    return HandleNotification.class;
                case QUERY:
                    return HandleQuery.class;
                case RESULT:
                    return HandleResult.class;
                case SCHEDULE:
                    return HandleSchedule.class;
                case USAGE:
                    return HandleUsage.class;
                default:
                    throw new ConfigurationException(String.format("Unrecognized type: %s", messageType));
            }
        }
    }

}
