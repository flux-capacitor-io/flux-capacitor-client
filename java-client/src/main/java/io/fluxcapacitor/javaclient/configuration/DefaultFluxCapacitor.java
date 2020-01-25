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
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.modeling.AggregateRepository;
import io.fluxcapacitor.javaclient.modeling.CompositeAggregateRepository;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.caching.CachingAggregateRepository;
import io.fluxcapacitor.javaclient.persisting.caching.DefaultCache;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.DefaultEventSourcingHandlerFactory;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.DefaultEventStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.DefaultSnapshotRepository;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandlerFactory;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingRepository;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStoreSerializer;
import io.fluxcapacitor.javaclient.persisting.keyvalue.DefaultKeyValueStore;
import io.fluxcapacitor.javaclient.persisting.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.publishing.CommandGateway;
import io.fluxcapacitor.javaclient.publishing.DefaultCommandGateway;
import io.fluxcapacitor.javaclient.publishing.DefaultErrorGateway;
import io.fluxcapacitor.javaclient.publishing.DefaultEventGateway;
import io.fluxcapacitor.javaclient.publishing.DefaultGenericGateway;
import io.fluxcapacitor.javaclient.publishing.DefaultMetricsGateway;
import io.fluxcapacitor.javaclient.publishing.DefaultQueryGateway;
import io.fluxcapacitor.javaclient.publishing.DefaultRequestHandler;
import io.fluxcapacitor.javaclient.publishing.DefaultResultGateway;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.publishing.EventGateway;
import io.fluxcapacitor.javaclient.publishing.MetricsGateway;
import io.fluxcapacitor.javaclient.publishing.QueryGateway;
import io.fluxcapacitor.javaclient.publishing.RequestGateway;
import io.fluxcapacitor.javaclient.publishing.RequestHandler;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.publishing.correlation.CorrelatingInterceptor;
import io.fluxcapacitor.javaclient.publishing.dataprotection.DataProtectionInterceptor;
import io.fluxcapacitor.javaclient.publishing.routing.MessageRoutingInterceptor;
import io.fluxcapacitor.javaclient.scheduling.DefaultScheduler;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.DefaultTracking;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import io.fluxcapacitor.javaclient.tracking.TrackingException;
import io.fluxcapacitor.javaclient.tracking.handling.DefaultHandlerFactory;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleError;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import io.fluxcapacitor.javaclient.tracking.handling.HandleMetrics;
import io.fluxcapacitor.javaclient.tracking.handling.HandleNotification;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import io.fluxcapacitor.javaclient.tracking.handling.HandleResult;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.errorreporting.ErrorReportingInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidatingInterceptor;
import io.fluxcapacitor.javaclient.tracking.metrics.HandlerMonitor;
import io.fluxcapacitor.javaclient.tracking.metrics.TrackerMonitor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.ERROR;
import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.MessageType.METRICS;
import static io.fluxcapacitor.common.MessageType.NOTIFICATION;
import static io.fluxcapacitor.common.MessageType.QUERY;
import static io.fluxcapacitor.common.MessageType.RESULT;
import static io.fluxcapacitor.common.MessageType.SCHEDULE;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.defaultParameterResolvers;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
public class DefaultFluxCapacitor implements FluxCapacitor {

    private final Map<MessageType, ? extends Tracking> trackingSupplier;
    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;
    private final EventGateway eventGateway;
    private final ResultGateway resultGateway;
    private final ErrorGateway errorGateway;
    private final MetricsGateway metricsGateway;
    private final AggregateRepository aggregateRepository;
    private final EventStore eventStore;
    private final KeyValueStore keyValueStore;
    private final Scheduler scheduler;
    private final Cache cache;
    private final Client client;
    private final Runnable shutdownHandler;

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Tracking tracking(MessageType messageType) {
        return Optional.ofNullable(trackingSupplier.get(messageType)).orElseThrow(
                () -> new TrackingException(String.format("Tracking is not supported for type %s", messageType)));
    }

    @Override
    public void close() {
        shutdownHandler.run();
    }

    public static class Builder implements FluxCapacitorBuilder {

        private Serializer serializer = new JacksonSerializer();
        private Serializer snapshotSerializer = serializer;
        private final Map<MessageType, List<ConsumerConfiguration>> consumerConfigurations = defaultConfigurations();
        private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers =
                new ArrayList<>(defaultParameterResolvers);
        private final Map<MessageType, DispatchInterceptor> dispatchInterceptors =
                Arrays.stream(MessageType.values()).collect(toMap(identity(), m -> (f, messageType) -> f));
        private final Map<MessageType, HandlerInterceptor> handlerInterceptors =
                Arrays.stream(MessageType.values()).collect(toMap(identity(), m -> (f, h, c) -> f));
        private DispatchInterceptor messageRoutingInterceptor = new MessageRoutingInterceptor();
        private Cache cache = new DefaultCache();
        private boolean disableErrorReporting;
        private boolean disableMessageCorrelation;
        private boolean disablePayloadValidation;
        private boolean disableDataProtection;
        private boolean disableAutomaticAggregateCaching;
        private boolean disableShutdownHook;
        private boolean collectTrackingMetrics;

        protected Map<MessageType, List<ConsumerConfiguration>> defaultConfigurations() {
            return unmodifiableMap(stream(MessageType.values()).collect(toMap(identity(), messageType ->
                    new ArrayList<>(singletonList(ConsumerConfiguration.getDefault(messageType))))));
        }

        @Override
        public Builder replaceSerializer(@NonNull Serializer serializer) {
            if (snapshotSerializer == this.serializer) {
                snapshotSerializer = serializer;
            }
            this.serializer = serializer;
            return this;
        }

        @Override
        public Builder replaceSnapshotSerializer(@NonNull Serializer serializer) {
            this.snapshotSerializer = serializer;
            return this;
        }

        @Override
        public Builder configureDefaultConsumer(@NonNull MessageType messageType,
                                                @NonNull UnaryOperator<ConsumerConfiguration> updateFunction) {
            List<ConsumerConfiguration> configurations = consumerConfigurations.get(messageType);
            ConsumerConfiguration defaultConfiguration = configurations.get(configurations.size() - 1);
            ConsumerConfiguration updatedConfiguration = updateFunction.apply(defaultConfiguration);
            if (configurations.subList(0, configurations.size() - 1).stream()
                    .map(ConsumerConfiguration::getName)
                    .anyMatch(n -> Objects.equals(n, updatedConfiguration.getName()))) {
                throw new IllegalArgumentException(
                        format("Consumer name %s is already in use", updatedConfiguration.getName()));
            }
            configurations.set(configurations.size() - 1, updatedConfiguration);
            return this;
        }

        @Override
        public Builder addConsumerConfiguration(@NonNull ConsumerConfiguration consumerConfiguration) {
            List<ConsumerConfiguration> configurations =
                    consumerConfigurations.get(consumerConfiguration.getMessageType());
            if (configurations.stream().map(ConsumerConfiguration::getName)
                    .anyMatch(n -> Objects.equals(n, consumerConfiguration.getName()))) {
                throw new IllegalArgumentException(
                        format("Consumer name %s is already in use", consumerConfiguration.getName()));
            }
            configurations.add(configurations.size() - 1, consumerConfiguration);
            return this;
        }

        @Override
        public Builder addParameterResolver(@NonNull ParameterResolver<DeserializingMessage> parameterResolver) {
            parameterResolvers.add(parameterResolver);
            return this;
        }

        @Override
        public Builder addDispatchInterceptor(@NonNull DispatchInterceptor interceptor, MessageType... forTypes) {
            Arrays.stream(forTypes.length == 0 ? MessageType.values() : forTypes)
                    .forEach(type -> dispatchInterceptors.computeIfPresent(type, (t, i) -> i.merge(interceptor)));
            return this;
        }

        @Override
        public Builder addHandlerInterceptor(@NonNull HandlerInterceptor interceptor, MessageType... forTypes) {
            Arrays.stream(forTypes.length == 0 ? MessageType.values() : forTypes)
                    .forEach(type -> handlerInterceptors.computeIfPresent(type, (t, i) -> i.merge(interceptor)));
            return this;
        }

        @Override
        public Builder replaceMessageRoutingInterceptor(@NonNull DispatchInterceptor messageRoutingInterceptor) {
            this.messageRoutingInterceptor = messageRoutingInterceptor;
            return this;
        }

        @Override
        public FluxCapacitorBuilder replaceCache(@NonNull Cache cache) {
            this.cache = cache;
            return this;
        }

        @Override
        public FluxCapacitorBuilder disableErrorReporting() {
            disableErrorReporting = true;
            return this;
        }

        @Override
        public FluxCapacitorBuilder disableShutdownHook() {
            disableShutdownHook = true;
            return this;
        }

        @Override
        public Builder disableMessageCorrelation() {
            disableMessageCorrelation = true;
            return this;
        }

        @Override
        public Builder disablePayloadValidation() {
            disablePayloadValidation = true;
            return this;
        }

        @Override
        public FluxCapacitorBuilder disableDataProtection() {
            disableDataProtection = true;
            return this;
        }

        @Override
        public FluxCapacitorBuilder disableAutomaticAggregateCaching() {
            disableAutomaticAggregateCaching = true;
            return this;
        }

        @Override
        public FluxCapacitorBuilder enableTrackingMetrics() {
            collectTrackingMetrics = true;
            return this;
        }

        @Override
        public FluxCapacitor build(@NonNull Client client) {
            Map<MessageType, DispatchInterceptor> dispatchInterceptors = new HashMap<>(this.dispatchInterceptors);
            Map<MessageType, HandlerInterceptor> handlerInterceptors = new HashMap<>(this.handlerInterceptors);
            Map<MessageType, List<ConsumerConfiguration>> consumerConfigurations =
                    new HashMap<>(this.consumerConfigurations);


            KeyValueStore keyValueStore = new DefaultKeyValueStore(client.getKeyValueClient(), serializer);

            //enable message routing
            Arrays.stream(MessageType.values()).forEach(
                    type -> dispatchInterceptors.computeIfPresent(type, (t, i) -> i.merge(messageRoutingInterceptor)));

            //enable data protection
            if (!disableDataProtection) {
                DataProtectionInterceptor interceptor = new DataProtectionInterceptor(keyValueStore, serializer);
                Stream.of(COMMAND, EVENT, QUERY, RESULT, SCHEDULE).forEach(type -> {
                    dispatchInterceptors.computeIfPresent(type, (t, i) -> i.merge(interceptor));
                    handlerInterceptors.computeIfPresent(type, (t, i) -> i.merge(interceptor));
                });
            }

            //enable message correlation
            if (!disableMessageCorrelation) {
                CorrelatingInterceptor correlatingInterceptor = new CorrelatingInterceptor(client);
                Arrays.stream(MessageType.values()).forEach(
                        type -> dispatchInterceptors.compute(type, (t, i) -> correlatingInterceptor.merge(i)));
            }

            //enable command and query validation
            if (!disablePayloadValidation) {
                Stream.of(COMMAND, QUERY).forEach(type -> handlerInterceptors
                        .computeIfPresent(type, (t, i) -> i.merge(new ValidatingInterceptor())));
            }

            //collect metrics about consumers and handlers
            if (collectTrackingMetrics) {
                BatchInterceptor batchInterceptor = new TrackerMonitor();
                HandlerMonitor handlerMonitor = new HandlerMonitor();
                Arrays.stream(MessageType.values()).forEach(type -> {
                    consumerConfigurations.computeIfPresent(type, (t, list) ->
                            t == METRICS ? list : list.stream().map(c -> c.toBuilder().trackingConfiguration(
                                    c.getTrackingConfiguration().toBuilder().batchInterceptor(batchInterceptor).build())
                                    .build()).collect(toList()));
                    handlerInterceptors.compute(type, (t, i) -> t == METRICS ? i : handlerMonitor.merge(i));
                });
            }

            //event sourcing
            EventSourcingHandlerFactory eventSourcingHandlerFactory =
                    new DefaultEventSourcingHandlerFactory(parameterResolvers);
            EventStore eventStore = new DefaultEventStore(client.getEventStoreClient(),
                                                          new EventStoreSerializer(serializer,
                                                                                   dispatchInterceptors.get(EVENT)),
                                                          new DefaultHandlerFactory(EVENT,
                                                                                    handlerInterceptors.get(EVENT),
                                                                                    parameterResolvers));
            DefaultSnapshotRepository snapshotRepository =
                    new DefaultSnapshotRepository(client.getKeyValueClient(), snapshotSerializer);

            AggregateRepository aggregateRepository = new CompositeAggregateRepository(
                    new EventSourcingRepository(eventStore, snapshotRepository, cache, eventSourcingHandlerFactory));

            if (!disableAutomaticAggregateCaching) {
                aggregateRepository =
                        new CachingAggregateRepository(aggregateRepository, eventSourcingHandlerFactory, cache,
                                                       client.name(), client.getTrackingClient(NOTIFICATION),
                                                       serializer);
            }

            //enable error reporter as the outermost handler interceptor
            ErrorGateway errorGateway =
                    new DefaultErrorGateway(client.getGatewayClient(ERROR),
                                            new MessageSerializer(serializer, dispatchInterceptors.get(ERROR),
                                                                  ERROR));
            if (!disableErrorReporting) {
                ErrorReportingInterceptor interceptor = new ErrorReportingInterceptor(errorGateway);
                Arrays.stream(MessageType.values())
                        .forEach(type -> handlerInterceptors.compute(type, (t, i) -> interceptor.merge(i)));
            }

            //create gateways
            ResultGateway resultGateway =
                    new DefaultResultGateway(client.getGatewayClient(RESULT),
                                             new MessageSerializer(serializer, dispatchInterceptors.get(RESULT),
                                                                   RESULT));
            RequestHandler requestHandler =
                    new DefaultRequestHandler(client.getTrackingClient(RESULT), serializer, client.name(), client.id());
            CommandGateway commandGateway =
                    new DefaultCommandGateway(createRequestGateway(client, COMMAND, requestHandler,
                                                                   dispatchInterceptors.get(COMMAND),
                                                                   new DefaultHandlerFactory(COMMAND,
                                                                                             handlerInterceptors
                                                                                                     .get(COMMAND),
                                                                                             parameterResolvers)));
            QueryGateway queryGateway =
                    new DefaultQueryGateway(createRequestGateway(client, QUERY, requestHandler,
                                                                 dispatchInterceptors.get(QUERY),
                                                                 new DefaultHandlerFactory(QUERY,
                                                                                           handlerInterceptors
                                                                                                   .get(QUERY),
                                                                                           parameterResolvers)));
            EventGateway eventGateway =
                    new DefaultEventGateway(client.getGatewayClient(EVENT),
                                            new MessageSerializer(serializer, dispatchInterceptors.get(EVENT),
                                                                  EVENT),
                                            new DefaultHandlerFactory(EVENT, handlerInterceptors.get(EVENT),
                                                                      parameterResolvers));

            MetricsGateway metricsGateway =
                    new DefaultMetricsGateway(client.getGatewayClient(METRICS),
                                              new MessageSerializer(serializer, dispatchInterceptors.get(METRICS),
                                                                    METRICS));


            //tracking
            Map<MessageType, Tracking> trackingMap = stream(MessageType.values())
                    .collect(toMap(identity(),
                                   m -> new DefaultTracking(m, getHandlerAnnotation(m), client.getTrackingClient(m),
                                                            resultGateway, consumerConfigurations.get(m), serializer,
                                                            handlerInterceptors.get(m), parameterResolvers)));

            //misc
            Scheduler scheduler = new DefaultScheduler(client.getSchedulingClient(),
                                                       new MessageSerializer(serializer,
                                                                             dispatchInterceptors.get(SCHEDULE),
                                                                             SCHEDULE));
            AtomicBoolean closed = new AtomicBoolean();
            Runnable shutdownHandler = () -> {
                if (closed.compareAndSet(false, true)) {
                    log.info("Initiating controlled shutdown");
                    ForkJoinPool.commonPool().invokeAll(trackingMap.values().stream().map(t -> (Callable<?>) () -> {
                        t.close();
                        return null;
                    }).collect(toList()));
                    requestHandler.close();
                    client.shutDown();
                    log.info("Completed shutdown");
                }
            };

            //and finally...
            FluxCapacitor fluxCapacitor = doBuild(trackingMap, commandGateway, queryGateway, eventGateway,
                                                  resultGateway, errorGateway, metricsGateway, aggregateRepository,
                                                  eventStore, keyValueStore, scheduler, cache, client, shutdownHandler);

            //perform a controlled shutdown when the vm exits
            if (!disableShutdownHook) {
                getRuntime().addShutdownHook(new Thread(shutdownHandler));
            }

            return fluxCapacitor;
        }

        protected FluxCapacitor doBuild(Map<MessageType, ? extends Tracking> trackingSupplier,
                                        CommandGateway commandGateway, QueryGateway queryGateway,
                                        EventGateway eventGateway, ResultGateway resultGateway,
                                        ErrorGateway errorGateway, MetricsGateway metricsGateway,
                                        AggregateRepository aggregateRepository,
                                        EventStore eventStore, KeyValueStore keyValueStore, Scheduler scheduler,
                                        Cache cache, Client client, Runnable shutdownHandler) {
            return new DefaultFluxCapacitor(trackingSupplier, commandGateway, queryGateway, eventGateway, resultGateway,
                                            errorGateway, metricsGateway, aggregateRepository, eventStore,
                                            keyValueStore, scheduler,
                                            cache, client, shutdownHandler);
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
                case ERROR:
                    return HandleError.class;
                case SCHEDULE:
                    return HandleSchedule.class;
                case METRICS:
                    return HandleMetrics.class;
                default:
                    throw new ConfigurationException(String.format("Unrecognized type: %s", messageType));
            }
        }

        protected RequestGateway createRequestGateway(Client client, MessageType messageType,
                                                      RequestHandler requestHandler,
                                                      DispatchInterceptor dispatchInterceptor,
                                                      DefaultHandlerFactory handlerFactory) {
            return new DefaultGenericGateway(messageType, client.getGatewayClient(messageType), requestHandler,
                                             new MessageSerializer(serializer, dispatchInterceptor, messageType),
                                             handlerFactory);
        }
    }

}
