/*
 * Copyright (c) 2016-2018 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import io.fluxcapacitor.javaclient.tracking.*;
import io.fluxcapacitor.javaclient.tracking.handling.*;
import io.fluxcapacitor.javaclient.tracking.handling.MetadataParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.PayloadParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidatingInterceptor;
import io.fluxcapacitor.javaclient.tracking.metrics.HandlerMonitor;
import io.fluxcapacitor.javaclient.tracking.metrics.TrackerMonitor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.common.MessageType.*;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class DefaultFluxCapacitor implements FluxCapacitor {

    private final Map<MessageType, Tracking> trackingSupplier;
    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;
    private final EventGateway eventGateway;
    private final ResultGateway resultGateway;
    private final MetricsGateway metricsGateway;
    private final EventSourcing eventSourcing;
    private final KeyValueStore keyValueStore;
    private final Scheduler scheduler;
    private final Client client;

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
    public MetricsGateway metricsGateway() {
        return metricsGateway;
    }

    @Override
    public Tracking tracking(MessageType messageType) {
        return Optional.ofNullable(trackingSupplier.get(messageType)).orElseThrow(
                () -> new TrackingException(String.format("Tracking is not supported for type %s", messageType)));
    }

    @Override
    public Client client() {
        return client;
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
                Arrays.stream(MessageType.values()).collect(toMap(identity(), m -> (f, h, c) -> f));
        private final Set<CorrelationDataProvider> correlationDataProviders = new LinkedHashSet<>();
        private DispatchInterceptor messageRoutingInterceptor = new MessageRoutingInterceptor();
        private boolean disableMessageCorrelation;
        private boolean disableCommandValidation;
        private boolean collectTrackingMetrics;
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
        public FluxCapacitorBuilder collectTrackingMetrics() {
            collectTrackingMetrics = true;
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
            Map<MessageType, List<ConsumerConfiguration>> consumerConfigurations =
                    new HashMap<>(this.consumerConfigurations);

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

            //collect metrics about consumers and handlers
            if (collectTrackingMetrics) {
                BatchInterceptor batchInterceptor = new TrackerMonitor();
                HandlerMonitor handlerMonitor = new HandlerMonitor();
                Arrays.stream(MessageType.values()).forEach(type -> {
                    consumerConfigurations.compute(type, (t, list) ->
                            t == METRICS ? list : list.stream().map(c -> c.toBuilder().trackingConfiguration(
                                    c.getTrackingConfiguration().toBuilder().batchInterceptor(batchInterceptor).build())
                            .build()).collect(toList()));
                    handlerInterceptors.compute(type, (t, i) -> t == METRICS ? i : handlerMonitor.merge(i));
                });
            }

            //create gateways
            ResultGateway resultGateway =
                    new DefaultResultGateway(client.getGatewayClient(RESULT),
                                             new MessageSerializer(serializer, dispatchInterceptors.get(RESULT),
                                                                   RESULT));
            RequestHandler requestHandler =
                    new DefaultRequestHandler(client.getTrackingClient(RESULT), serializer, client.id());
            CommandGateway commandGateway =
                    new DefaultCommandGateway(client.getGatewayClient(COMMAND), requestHandler,
                                              new MessageSerializer(serializer, dispatchInterceptors.get(COMMAND),
                                                                    COMMAND));
            QueryGateway queryGateway =
                    new DefaultQueryGateway(client.getGatewayClient(QUERY), requestHandler,
                                            new MessageSerializer(serializer, dispatchInterceptors.get(QUERY), QUERY));
            EventGateway eventGateway =
                    new DefaultEventGateway(client.getGatewayClient(EVENT),
                                            new MessageSerializer(serializer, dispatchInterceptors.get(EVENT), EVENT));
            MetricsGateway metricsGateway =
                    new DefaultMetricsGateway(client.getGatewayClient(METRICS), new MessageSerializer(
                            serializer, dispatchInterceptors.get(METRICS), METRICS));

            //event sourcing
            EventStore eventStore = new DefaultEventStore(client.getEventStoreClient(),
                                                          new EventStoreSerializer(serializer,
                                                                                   dispatchInterceptors.get(EVENT)));
            DefaultSnapshotRepository snapshotRepository =
                    new DefaultSnapshotRepository(client.getKeyValueClient(), snapshotSerializer);
            DefaultEventSourcing eventSourcing =
                    new DefaultEventSourcing(eventStore, snapshotRepository, new DefaultCache());

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
                                                                             dispatchInterceptors.get(SCHEDULE),
                                                                             SCHEDULE));

            //and finally...
            return doBuild(trackingMap, commandGateway, queryGateway, eventGateway, resultGateway, metricsGateway,
                           eventSourcing, keyValueStore, scheduler, client);
        }

        protected FluxCapacitor doBuild(Map<MessageType, Tracking> trackingSupplier,
                                        CommandGateway commandGateway, QueryGateway queryGateway,
                                        EventGateway eventGateway, ResultGateway resultGateway,
                                        MetricsGateway metricsGateway, EventSourcing eventSourcing,
                                        KeyValueStore keyValueStore,
                                        Scheduler scheduler, Client client) {
            return new DefaultFluxCapacitor(trackingSupplier, commandGateway, queryGateway, eventGateway, resultGateway,
                                            metricsGateway, eventSourcing, keyValueStore, scheduler, client);
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
                case METRICS:
                    return HandleMetrics.class;
                default:
                    throw new ConfigurationException(String.format("Unrecognized type: %s", messageType));
            }
        }
    }

}
