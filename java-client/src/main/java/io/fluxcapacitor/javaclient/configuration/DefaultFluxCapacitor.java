package io.fluxcapacitor.javaclient.configuration;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.caching.DefaultCache;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.eventsourcing.*;
import io.fluxcapacitor.javaclient.keyvalue.DefaultKeyValueStore;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.publishing.*;
import io.fluxcapacitor.javaclient.publishing.correlation.CorrelatingInterceptor;
import io.fluxcapacitor.javaclient.publishing.correlation.CorrelationDataProvider;
import io.fluxcapacitor.javaclient.publishing.correlation.MessageOriginProvider;
import io.fluxcapacitor.javaclient.publishing.routing.MessageRoutingInterceptor;
import io.fluxcapacitor.javaclient.scheduling.DefaultScheduler;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.DefaultTracking;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import io.fluxcapacitor.javaclient.tracking.TrackingException;
import io.fluxcapacitor.javaclient.tracking.handling.*;
import io.fluxcapacitor.javaclient.tracking.handling.MetadataParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.PayloadParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidatingInterceptor;
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
    private final EventGateway eventGateway;
    private final ResultGateway resultGateway;
    private final EventSourcing eventSourcing;
    private final KeyValueStore keyValueStore;
    private final Scheduler scheduler;

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public EventSourcing eventSourcing() {
        return eventSourcing;
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
    public EventGateway eventGateway() {
        return eventGateway;
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

    public static class Builder implements FluxCapacitorBuilder {

        private Serializer serializer = new JacksonSerializer();
        private Serializer snapshotSerializer = serializer;
        private final Map<MessageType, List<ConsumerConfiguration>> consumerConfigurations = defaultConfigurations();
        private final List<ParameterResolver<? super DeserializingMessage>> trackingParameterResolvers =
                defaultTrackingParameterResolvers();
        private final Map<MessageType, DispatchInterceptor> dispatchInterceptors =
                Arrays.stream(MessageType.values()).collect(toMap(identity(), m -> f -> f));
        private final Map<MessageType, HandlerInterceptor> handlerInterceptors =
                Arrays.stream(MessageType.values()).collect(toMap(identity(), m -> f -> f));
        private final Set<CorrelationDataProvider> correlationDataProviders = new LinkedHashSet<>();
        private DispatchInterceptor messageRoutingInterceptor = new MessageRoutingInterceptor();
        private boolean disableMessageCorrelation;
        private boolean disableCommandValidation;
        private HandlerInterceptor commandValidationInterceptor = new ValidatingInterceptor();

        protected List<ParameterResolver<? super DeserializingMessage>> defaultTrackingParameterResolvers() {
            return new ArrayList<>(Arrays.asList(new PayloadParameterResolver(), new MetadataParameterResolver()));
        }

        protected Map<MessageType, List<ConsumerConfiguration>> defaultConfigurations() {
            return unmodifiableMap(stream(MessageType.values()).collect(toMap(identity(), messageType ->
                    new ArrayList<>(singletonList(ConsumerConfiguration.getDefault(messageType))))));
        }

        @Override
        public Builder serializer(Serializer serializer) {
            if (snapshotSerializer == this.serializer) {
                snapshotSerializer = serializer;
            }
            this.serializer = serializer;
            return this;
        }

        @Override
        public Builder snapshotSerializer(Serializer serializer) {
            this.snapshotSerializer = serializer;
            return this;
        }

        @Override
        public Builder configureDefaultConsumer(MessageType messageType,
                                                UnaryOperator<ConsumerConfiguration> updateFunction) {
            List<ConsumerConfiguration> configurations = consumerConfigurations.get(messageType);
            ConsumerConfiguration defaultConfiguration = configurations.get(configurations.size() - 1);
            configurations.set(configurations.size() - 1, updateFunction.apply(defaultConfiguration));
            return this;
        }

        @Override
        public Builder addConsumerConfiguration(MessageType messageType, ConsumerConfiguration consumerConfiguration) {
            List<ConsumerConfiguration> configurations = consumerConfigurations.get(messageType);
            configurations.add(configurations.size() - 1, consumerConfiguration);
            return this;
        }

        @Override
        public Builder addTrackingParameterResolver(ParameterResolver<DeserializingMessage> parameterResolver) {
            trackingParameterResolvers.add(parameterResolver);
            return this;
        }

        @Override
        public Builder addDispatchInterceptor(DispatchInterceptor interceptor, MessageType... forTypes) {
            Arrays.stream(forTypes.length == 0 ? MessageType.values() : forTypes)
                    .forEach(type -> dispatchInterceptors.compute(type, (t, i) -> i.merge(interceptor)));
            return this;
        }

        @Override
        public Builder addHandlerInterceptor(HandlerInterceptor interceptor, MessageType... forTypes) {
            Arrays.stream(forTypes.length == 0 ? MessageType.values() : forTypes)
                    .forEach(type -> handlerInterceptors.compute(type, (t, i) -> i.merge(interceptor)));
            return this;
        }

        @Override
        public Builder addCorrelationDataProvider(CorrelationDataProvider dataProvider) {
            correlationDataProviders.add(dataProvider);
            return this;
        }

        @Override
        public Builder changeMessageRoutingInterceptor(DispatchInterceptor messageRoutingInterceptor) {
            this.messageRoutingInterceptor = messageRoutingInterceptor;
            return this;
        }

        @Override
        public Builder disableMessageCorrelation() {
            disableMessageCorrelation = true;
            return this;
        }

        @Override
        public Builder disableCommandValidation() {
            disableCommandValidation = true;
            return this;
        }

        @Override
        public Builder changeCommandValidationInterceptor(HandlerInterceptor validationInterceptor) {
            this.commandValidationInterceptor = validationInterceptor;
            return this;
        }

        @Override
        public FluxCapacitor build(Client client) {
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

            //enable command validation
            if (!disableCommandValidation) {
                handlerInterceptors.compute(COMMAND, (t, i) -> i.merge(commandValidationInterceptor));
            }

            //create gateways
            ResultGateway resultGateway =
                    new DefaultResultGateway(client.getGatewayClient(RESULT),
                                             new MessageSerializer(serializer, dispatchInterceptors.get(RESULT), RESULT));
            RequestHandler requestHandler =
                    new DefaultRequestHandler(client.getTrackingClient(RESULT), serializer, client.id());
            CommandGateway commandGateway =
                    new DefaultCommandGateway(client.getGatewayClient(COMMAND), requestHandler,
                                              new MessageSerializer(serializer, dispatchInterceptors.get(COMMAND), COMMAND));
            QueryGateway queryGateway =
                    new DefaultQueryGateway(client.getGatewayClient(QUERY), requestHandler,
                                            new MessageSerializer(serializer, dispatchInterceptors.get(QUERY), QUERY));
            EventGateway eventGateway =
                    new DefaultEventGateway(client.getGatewayClient(EVENT),
                                            new MessageSerializer(serializer, dispatchInterceptors.get(EVENT), EVENT));

            //event sourcing
            EventStore eventStore = new DefaultEventStore(client.getEventStoreClient(),
                                                          new EventStoreSerializer(serializer,
                                                                                   dispatchInterceptors.get(EVENT)));
            DefaultSnapshotRepository snapshotRepository =
                    new DefaultSnapshotRepository(client.getKeyValueClient(), snapshotSerializer);
            DefaultEventSourcing eventSourcing = new DefaultEventSourcing(eventStore, snapshotRepository, new DefaultCache());

            //register event sourcing as the outermost handler interceptor
            handlerInterceptors.compute(COMMAND, (t, i) -> eventSourcing.merge(i));

            //tracking
            Map<MessageType, Tracking> trackingMap = stream(MessageType.values())
                    .collect(toMap(identity(),
                                   m -> new DefaultTracking(m, getHandlerAnnotation(m), client.getTrackingClient(m),
                                                            resultGateway, consumerConfigurations.get(m), serializer,
                                                            handlerInterceptors.get(m), trackingParameterResolvers)));

            //misc
            KeyValueStore keyValueStore = new DefaultKeyValueStore(client.getKeyValueClient(), serializer);
            Scheduler scheduler = new DefaultScheduler(client.getSchedulingClient(),
                                                       new MessageSerializer(serializer,
                                                                             dispatchInterceptors.get(SCHEDULE), SCHEDULE));

            //and finally...
            return new DefaultFluxCapacitor(trackingMap, commandGateway, queryGateway, eventGateway, resultGateway,
                                            eventSourcing, keyValueStore, scheduler);
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
