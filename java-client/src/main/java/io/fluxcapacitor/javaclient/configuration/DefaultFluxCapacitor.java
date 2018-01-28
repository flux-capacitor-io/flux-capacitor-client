package io.fluxcapacitor.javaclient.configuration;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.eventsourcing.DefaultEventStore;
import io.fluxcapacitor.javaclient.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.eventsourcing.EventStoreSerializer;
import io.fluxcapacitor.javaclient.gateway.*;
import io.fluxcapacitor.javaclient.gateway.interceptors.CorrelatingInterceptor;
import io.fluxcapacitor.javaclient.gateway.interceptors.CorrelationDataProvider;
import io.fluxcapacitor.javaclient.gateway.interceptors.MessageOriginProvider;
import io.fluxcapacitor.javaclient.gateway.interceptors.MessageRoutingInterceptor;
import io.fluxcapacitor.javaclient.keyvalue.DefaultKeyValueStore;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.scheduling.DefaultScheduler;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.tracking.*;
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
        private final Map<MessageType, DispatchInterceptor> dispatchInterceptors =
                Arrays.stream(MessageType.values()).collect(toMap(identity(), m -> f -> f));
        private final Map<MessageType, HandlerInterceptor> handlerInterceptors =
                Arrays.stream(MessageType.values()).collect(toMap(identity(), m -> f -> f));
        private final Set<CorrelationDataProvider> correlationDataProviders = new LinkedHashSet<>();
        private DispatchInterceptor messageRoutingInterceptor = new MessageRoutingInterceptor();
        private boolean disableMessageCorrelation;

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

        public Builder addDispatchInterceptor(DispatchInterceptor interceptor, MessageType... forTypes) {
            Arrays.stream(forTypes).forEach(type -> dispatchInterceptors.get(type).merge(interceptor));
            return this;
        }

        public Builder addHandlerInterceptor(HandlerInterceptor interceptor, MessageType... forTypes) {
            Arrays.stream(forTypes).forEach(type -> handlerInterceptors.get(type).merge(interceptor));
            return this;
        }

        public Builder addCorrelationDataProvider(CorrelationDataProvider dataProvider) {
            correlationDataProviders.add(dataProvider);
            return this;
        }

        public Builder changeMessageRoutingInterceptor(DispatchInterceptor messageRoutingInterceptor) {
            this.messageRoutingInterceptor = messageRoutingInterceptor;
            return this;
        }

        public Builder disableMessageCorrelation() {
            disableMessageCorrelation = true;
            return this;
        }

        public FluxCapacitor build(FluxCapacitorClient client) {
            Map<MessageType, DispatchInterceptor> dispatchInterceptors = new HashMap<>(this.dispatchInterceptors);
            Map<MessageType, HandlerInterceptor> handlerInterceptors = new HashMap<>(this.handlerInterceptors);

            //enable message routing
            Arrays.stream(MessageType.values())
                    .forEach(type -> dispatchInterceptors.compute(type, (t, i) -> i.merge(messageRoutingInterceptor)));

            //enable message correlation
            if (!disableMessageCorrelation) {
                Set<CorrelationDataProvider> dataProviders = new LinkedHashSet<>(this.correlationDataProviders);
                dataProviders.add(new MessageOriginProvider());
                CorrelatingInterceptor correlatingInterceptor = new CorrelatingInterceptor(dataProviders);
                Arrays.stream(MessageType.values()).forEach(type -> {
                    dispatchInterceptors.compute(type, (t, i) -> correlatingInterceptor.merge(i));
                    handlerInterceptors.compute(type, (t, i) -> correlatingInterceptor.merge(i));
                });
            }

            ResultGateway resultGateway =
                    new DefaultResultGateway(client.getGatewayClient(RESULT),
                                             new MessageSerializer(serializer, dispatchInterceptors.get(RESULT)));
            RequestHandler requestHandler =
                    new DefaultRequestHandler(client.getTrackingClient(RESULT), client.getProperties());
            CommandGateway commandGateway =
                    new DefaultCommandGateway(client.getGatewayClient(COMMAND), requestHandler,
                                              new MessageSerializer(serializer, dispatchInterceptors.get(COMMAND)));
            QueryGateway queryGateway =
                    new DefaultQueryGateway(client.getGatewayClient(QUERY), requestHandler,
                                            new MessageSerializer(serializer, dispatchInterceptors.get(QUERY)));
            KeyValueStore keyValueStore = new DefaultKeyValueStore(client.getKeyValueClient(), serializer);
            EventStore eventStore = new DefaultEventStore(
                    client.getEventStoreClient(), client.getGatewayClient(EVENT), keyValueStore,
                    new EventStoreSerializer(serializer, dispatchInterceptors.get(EVENT)));
            Scheduler scheduler = new DefaultScheduler(client.getSchedulingClient(),
                                                       new MessageSerializer(serializer,
                                                                             dispatchInterceptors.get(SCHEDULE)));
            Map<MessageType, Tracking> trackingMap = stream(MessageType.values())
                    .collect(toMap(identity(),
                                   m -> new DefaultTracking(getHandlerAnnotation(m), client.getTrackingClient(m),
                                                            resultGateway, consumerConfigurations.get(m), serializer,
                                                            handlerInterceptors.get(m), parameterResolvers)));
            return new DefaultFluxCapacitor(trackingMap, commandGateway, queryGateway, resultGateway, eventStore,
                                            keyValueStore, scheduler);
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
