/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

import io.fluxcapacitor.common.DelegatingClock;
import io.fluxcapacitor.common.InMemoryTaskScheduler;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.TaskScheduler;
import io.fluxcapacitor.common.ThrowingRunnable;
import io.fluxcapacitor.common.application.DecryptingPropertySource;
import io.fluxcapacitor.common.application.DefaultPropertySource;
import io.fluxcapacitor.common.application.PropertySource;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.IdentityProvider;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.modeling.DefaultEntityHelper;
import io.fluxcapacitor.javaclient.modeling.DefaultHandlerRepository;
import io.fluxcapacitor.javaclient.modeling.EntityParameterResolver;
import io.fluxcapacitor.javaclient.modeling.HandlerRepository;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.caching.CacheEvictionsLogger;
import io.fluxcapacitor.javaclient.persisting.caching.DefaultCache;
import io.fluxcapacitor.javaclient.persisting.caching.NamedCache;
import io.fluxcapacitor.javaclient.persisting.caching.SelectiveCache;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.DefaultEventStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.DefaultSnapshotStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.SnapshotStore;
import io.fluxcapacitor.javaclient.persisting.keyvalue.DefaultKeyValueStore;
import io.fluxcapacitor.javaclient.persisting.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository;
import io.fluxcapacitor.javaclient.persisting.repository.CachingAggregateRepository;
import io.fluxcapacitor.javaclient.persisting.repository.DefaultAggregateRepository;
import io.fluxcapacitor.javaclient.persisting.search.DefaultDocumentStore;
import io.fluxcapacitor.javaclient.persisting.search.DocumentSerializer;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.persisting.search.client.InMemorySearchStore;
import io.fluxcapacitor.javaclient.persisting.search.client.LocalDocumentHandlerRegistry;
import io.fluxcapacitor.javaclient.publishing.*;
import io.fluxcapacitor.javaclient.publishing.correlation.CorrelatingInterceptor;
import io.fluxcapacitor.javaclient.publishing.correlation.CorrelationDataProvider;
import io.fluxcapacitor.javaclient.publishing.correlation.DefaultCorrelationDataProvider;
import io.fluxcapacitor.javaclient.publishing.dataprotection.DataProtectionInterceptor;
import io.fluxcapacitor.javaclient.publishing.routing.MessageRoutingInterceptor;
import io.fluxcapacitor.javaclient.scheduling.DefaultScheduler;
import io.fluxcapacitor.javaclient.scheduling.ScheduledCommandHandler;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.scheduling.SchedulingInterceptor;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.DefaultTracking;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import io.fluxcapacitor.javaclient.tracking.TrackingException;
import io.fluxcapacitor.javaclient.tracking.handling.DefaultHandlerFactory;
import io.fluxcapacitor.javaclient.tracking.handling.DefaultRepositoryProvider;
import io.fluxcapacitor.javaclient.tracking.handling.DefaultResponseMapper;
import io.fluxcapacitor.javaclient.tracking.handling.DeserializingMessageParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.DocumentHandlerDecorator;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerDecorator;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import io.fluxcapacitor.javaclient.tracking.handling.LocalHandlerRegistry;
import io.fluxcapacitor.javaclient.tracking.handling.MessageParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.MetadataParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.PayloadParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.RepositoryProvider;
import io.fluxcapacitor.javaclient.tracking.handling.ResponseMapper;
import io.fluxcapacitor.javaclient.tracking.handling.TriggerParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.AuthenticatingInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import io.fluxcapacitor.javaclient.tracking.handling.errorreporting.ErrorReportingInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidatingInterceptor;
import io.fluxcapacitor.javaclient.tracking.metrics.HandlerMonitor;
import io.fluxcapacitor.javaclient.tracking.metrics.TrackerMonitor;
import io.fluxcapacitor.javaclient.web.DefaultWebResponseMapper;
import io.fluxcapacitor.javaclient.web.ForwardingWebConsumer;
import io.fluxcapacitor.javaclient.web.LocalServerConfig;
import io.fluxcapacitor.javaclient.web.WebParamParameterResolver;
import io.fluxcapacitor.javaclient.web.WebPayloadParameterResolver;
import io.fluxcapacitor.javaclient.web.WebResponseCompressingInterceptor;
import io.fluxcapacitor.javaclient.web.WebResponseGateway;
import io.fluxcapacitor.javaclient.web.WebResponseMapper;
import io.fluxcapacitor.javaclient.web.WebsocketHandlerDecorator;
import io.fluxcapacitor.javaclient.web.WebsocketResponseInterceptor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.CUSTOM;
import static io.fluxcapacitor.common.MessageType.DOCUMENT;
import static io.fluxcapacitor.common.MessageType.ERROR;
import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.MessageType.METRICS;
import static io.fluxcapacitor.common.MessageType.NOTIFICATION;
import static io.fluxcapacitor.common.MessageType.QUERY;
import static io.fluxcapacitor.common.MessageType.RESULT;
import static io.fluxcapacitor.common.MessageType.SCHEDULE;
import static io.fluxcapacitor.common.MessageType.WEBREQUEST;
import static io.fluxcapacitor.common.MessageType.WEBRESPONSE;
import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;
import static io.fluxcapacitor.common.ObjectUtils.newThreadName;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Accessors(fluent = true)
public class DefaultFluxCapacitor implements FluxCapacitor {

    private final Map<MessageType, ? extends Tracking> trackingSupplier;
    private final Function<String, ? extends GenericGateway> customGatewaySupplier;
    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;
    private final EventGateway eventGateway;
    private final ResultGateway resultGateway;
    private final ErrorGateway errorGateway;
    private final MetricsGateway metricsGateway;
    private final WebRequestGateway webRequestGateway;
    private final AggregateRepository aggregateRepository;
    private final SnapshotStore snapshotStore;
    private final EventStore eventStore;
    private final KeyValueStore keyValueStore;
    private final DocumentStore documentStore;
    private final Scheduler scheduler;
    private final UserProvider userProvider;
    private final Cache cache;
    private final Serializer serializer;
    private final CorrelationDataProvider correlationDataProvider;
    private final IdentityProvider identityProvider;
    private final PropertySource propertySource;
    private final DelegatingClock clock;
    private final TaskScheduler taskScheduler;
    private final Client client;
    private final ThrowingRunnable shutdownHandler;

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Collection<Runnable> cleanupTasks = new CopyOnWriteArrayList<>();

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public GenericGateway customGateway(String topic) {
        return customGatewaySupplier.apply(topic);
    }

    @Override
    public Tracking tracking(MessageType messageType) {
        return Optional.ofNullable(trackingSupplier.get(messageType)).orElseThrow(
                () -> new TrackingException(String.format("Tracking is not supported for type %s", messageType)));
    }

    @Override
    public void withClock(@NonNull Clock clock) {
        this.clock.setDelegate(clock);
    }

    public Clock clock() {
        return clock;
    }

    @Override
    public Registration beforeShutdown(Runnable task) {
        cleanupTasks.add(task);
        return () -> cleanupTasks.remove(task);
    }

    @Override
    public void close(boolean silently) {
        if (closed.compareAndSet(false, true)) {
            if (!silently) {
                log.info("Initiating controlled shutdown");
            }
            try {
                cleanupTasks.forEach(ObjectUtils::tryRun);
                taskScheduler.shutdown();
                shutdownHandler.run();
            } catch (Exception e) {
                log.error("Encountered an error during shutdown", e);
            } finally {
                if (FluxCapacitor.applicationInstance.get() == this) {
                    FluxCapacitor.applicationInstance.set(null);
                }
            }
            if (!silently) {
                log.info("Completed shutdown");
            }
        }
    }

    public static class Builder implements FluxCapacitorBuilder {

        private Serializer serializer = new JacksonSerializer();
        private Serializer snapshotSerializer = serializer;
        private CorrelationDataProvider correlationDataProvider = DefaultCorrelationDataProvider.INSTANCE;
        private DocumentSerializer documentSerializer = (JacksonSerializer) serializer;

        private final Map<MessageType, ConsumerConfiguration> defaultConsumerConfigurations =
                stream(MessageType.values()).collect(toMap(identity(), this::getDefaultConsumerConfiguration));
        private final Map<MessageType, List<ConsumerConfiguration>> customConsumerConfigurations =
                stream(MessageType.values()).collect(toMap(identity(), messageType -> new ArrayList<>()));
        private final List<ParameterResolver<? super DeserializingMessage>> customParameterResolvers =
                new ArrayList<>();
        private final Map<MessageType, List<DispatchInterceptor>> lowPrioDispatchInterceptors = new HashMap<>();
        private final Map<MessageType, List<DispatchInterceptor>> highPrioDispatchInterceptors = new HashMap<>();
        private final Map<MessageType, List<HandlerDecorator>> lowPrioHandlerDecorators = new HashMap<>();
        private final Map<MessageType, List<HandlerDecorator>> highPrioHandlerDecorators = new HashMap<>();
        private final Map<MessageType, List<BatchInterceptor>> generalBatchInterceptors = new HashMap<>();
        private final DelegatingClock clock = new DelegatingClock();
        private DispatchInterceptor messageRoutingInterceptor = new MessageRoutingInterceptor();
        private SchedulingInterceptor schedulingInterceptor = new SchedulingInterceptor();
        private TaskScheduler taskScheduler = new InMemoryTaskScheduler("FluxTaskScheduler", clock, newCachedThreadPool(newThreadFactory("FluxTaskScheduler-worker")));
        private ForwardingWebConsumer forwardingWebConsumer;
        private Cache cache = new DefaultCache();
        private Cache relationshipsCache = new DefaultCache(100_000);
        private ResponseMapper defaultResponseMapper = new DefaultResponseMapper();
        private WebResponseMapper webResponseMapper = new DefaultWebResponseMapper();
        private boolean disableErrorReporting;
        private boolean disableMessageCorrelation;
        private boolean disablePayloadValidation;
        private boolean disableDataProtection;
        private boolean disableAutomaticAggregateCaching;
        private boolean disableScheduledCommandHandler;
        private boolean disableShutdownHook;
        private boolean disableTrackingMetrics;
        private boolean disableCacheEvictionMetrics;
        private boolean disableWebResponseCompression;
        private boolean disableAdhocDispatchInterceptor;
        private boolean makeApplicationInstance;
        private UserProvider userProvider = UserProvider.defaultUserProvider;
        private IdentityProvider identityProvider = IdentityProvider.defaultIdentityProvider;
        private PropertySource propertySource = DefaultPropertySource.getInstance();

        @Override
        public Builder replaceSerializer(@NonNull Serializer serializer) {
            if (snapshotSerializer == this.serializer) {
                snapshotSerializer = serializer;
            }
            if (documentSerializer == this.serializer && serializer instanceof DocumentSerializer) {
                documentSerializer = (DocumentSerializer) serializer;
            }
            this.serializer = serializer;
            return this;
        }

        @Override
        public FluxCapacitorBuilder replaceCorrelationDataProvider(
                @NonNull UnaryOperator<CorrelationDataProvider> replaceFunction) {
            correlationDataProvider = replaceFunction.apply(correlationDataProvider);
            return this;
        }

        @Override
        public Builder replaceSnapshotSerializer(@NonNull Serializer serializer) {
            this.snapshotSerializer = serializer;
            return this;
        }

        @Override
        public FluxCapacitorBuilder replaceDocumentSerializer(@NonNull DocumentSerializer documentSerializer) {
            this.documentSerializer = documentSerializer;
            return this;
        }

        @Override
        public FluxCapacitorBuilder registerUserProvider(@NonNull UserProvider userProvider) {
            this.userProvider = userProvider;
            return this;
        }

        @Override
        public FluxCapacitorBuilder replacePropertySource(UnaryOperator<PropertySource> replacer) {
            propertySource = replacer.apply(propertySource);
            return this;
        }

        @Override
        public Builder configureDefaultConsumer(@NonNull MessageType messageType,
                                                @NonNull UnaryOperator<ConsumerConfiguration> updateFunction) {
            ConsumerConfiguration defaultConfiguration = defaultConsumerConfigurations.get(messageType);
            ConsumerConfiguration updatedConfiguration = updateFunction.apply(defaultConfiguration);
            defaultConsumerConfigurations.put(messageType, updatedConfiguration);
            return this;
        }

        @Override
        public Builder addConsumerConfiguration(@NonNull ConsumerConfiguration configuration,
                                                MessageType... messageTypes) {
            if (messageTypes.length == 0) {
                messageTypes = MessageType.values();
            }
            for (MessageType messageType : messageTypes) {
                List<ConsumerConfiguration> configurations = customConsumerConfigurations.get(messageType);
                if (configurations.stream().map(ConsumerConfiguration::getName)
                        .anyMatch(n -> Objects.equals(n, configuration.getName()))) {
                    throw new IllegalArgumentException(
                            format("Consumer name %s is already in use", configuration.getName()));
                }
                configurations.add(configuration);
            }
            return this;
        }

        @Override
        public FluxCapacitorBuilder addBatchInterceptor(BatchInterceptor interceptor, MessageType... forTypes) {
            Arrays.stream(forTypes.length == 0 ? MessageType.values() : forTypes)
                    .forEach(type -> generalBatchInterceptors.computeIfAbsent(type, t -> new ArrayList<>())
                            .add(interceptor));
            return this;
        }

        @Override
        public Builder addDispatchInterceptor(@NonNull DispatchInterceptor interceptor, boolean highPriority,
                                              MessageType... forTypes) {
            Arrays.stream(forTypes.length == 0 ? MessageType.values() : forTypes)
                    .forEach(type -> (highPriority ? highPrioDispatchInterceptors : lowPrioDispatchInterceptors)
                            .computeIfAbsent(type, t -> new ArrayList<>()).add(interceptor));
            return this;
        }

        @Override
        public Builder addHandlerDecorator(@NonNull HandlerDecorator interceptor, boolean highPriority,
                                           MessageType... forTypes) {
            Arrays.stream(forTypes.length == 0 ? MessageType.values() : forTypes)
                    .forEach(type -> (highPriority ? highPrioHandlerDecorators : lowPrioHandlerDecorators)
                            .computeIfAbsent(type, t -> new ArrayList<>()).add(interceptor));
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
        public FluxCapacitorBuilder forwardWebRequestsToLocalServer(LocalServerConfig localServerConfig,
                                                                    UnaryOperator<ConsumerConfiguration> consumerConfigurator) {
            forwardingWebConsumer =
                    new ForwardingWebConsumer(localServerConfig,
                                              consumerConfigurator.apply(getDefaultConsumerConfiguration(WEBREQUEST)));
            return this;
        }

        @Override
        public FluxCapacitorBuilder replaceDefaultResponseMapper(ResponseMapper defaultResponseMapper) {
            this.defaultResponseMapper = defaultResponseMapper;
            return this;
        }

        @Override
        public FluxCapacitorBuilder replaceWebResponseMapper(WebResponseMapper webResponseMapper) {
            this.webResponseMapper = webResponseMapper;
            return this;
        }

        @Override
        public FluxCapacitorBuilder replaceTaskScheduler(Function<Clock, TaskScheduler> function) {
            this.taskScheduler = function.apply(clock);
            return this;
        }

        @Override
        public FluxCapacitorBuilder withAggregateCache(Class<?> aggregateType, Cache cache) {
            this.cache = new SelectiveCache(cache, SelectiveCache.aggregateSelector(aggregateType), this.cache);
            return this;
        }

        @Override
        public FluxCapacitorBuilder replaceRelationshipsCache(UnaryOperator<Cache> replaceFunction) {
            relationshipsCache = replaceFunction.apply(relationshipsCache);
            return this;
        }

        @Override
        public FluxCapacitorBuilder replaceIdentityProvider(UnaryOperator<IdentityProvider> replaceFunction) {
            identityProvider = replaceFunction.apply(identityProvider);
            return this;
        }

        @Override
        public Builder addParameterResolver(
                @NonNull ParameterResolver<? super DeserializingMessage> parameterResolver) {
            customParameterResolvers.add(parameterResolver);
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
        public FluxCapacitorBuilder disableScheduledCommandHandler() {
            disableScheduledCommandHandler = true;
            return this;
        }

        @Override
        public FluxCapacitorBuilder disableTrackingMetrics() {
            disableTrackingMetrics = true;
            return this;
        }

        @Override
        public FluxCapacitorBuilder disableCacheEvictionMetrics() {
            disableCacheEvictionMetrics = true;
            return this;
        }

        @Override
        public FluxCapacitorBuilder disableWebResponseCompression() {
            disableWebResponseCompression = true;
            return this;
        }

        @Override
        public FluxCapacitorBuilder disableAdhocDispatchInterceptor() {
            disableAdhocDispatchInterceptor = true;
            return this;
        }

        @Override
        public FluxCapacitorBuilder makeApplicationInstance(boolean makeApplicationInstance) {
            this.makeApplicationInstance = makeApplicationInstance;
            return this;
        }

        @Override
        public FluxCapacitor build(@NonNull Client client) {
            Map<MessageType, DispatchInterceptor> dispatchInterceptors =
                    Arrays.stream(MessageType.values()).collect(toMap(identity(), m -> DispatchInterceptor.noOp));
            Map<MessageType, HandlerDecorator> handlerDecorators =
                    Arrays.stream(MessageType.values()).collect(toMap(identity(), m -> HandlerDecorator.noOp));
            Map<MessageType, List<ConsumerConfiguration>> consumerConfigurations =
                    new HashMap<>(this.customConsumerConfigurations);
            this.defaultConsumerConfigurations.forEach((type, config) -> consumerConfigurations.get(type).add(
                    config.toBuilder().name(String.format("%s_%s", client.name(), config.getName())).build()));

            KeyValueStore keyValueStore = new DefaultKeyValueStore(client.getKeyValueClient(), serializer);

            //enable message routing
            Arrays.stream(MessageType.values()).forEach(
                    type -> dispatchInterceptors.computeIfPresent(type,
                                                                  (t, i) -> i.andThen(messageRoutingInterceptor)));

            //enable authentication
            if (userProvider != null) {
                AuthenticatingInterceptor interceptor = new AuthenticatingInterceptor(userProvider);
                Stream.of(COMMAND, QUERY, SCHEDULE, WEBREQUEST).forEach(type -> {
                    dispatchInterceptors.computeIfPresent(type, (t, i) -> i.andThen(interceptor));
                    handlerDecorators.computeIfPresent(type, (t, i) -> i.andThen(interceptor));
                });
            }

            //enable data protection
            if (!disableDataProtection) {
                DataProtectionInterceptor interceptor = new DataProtectionInterceptor(keyValueStore, serializer);
                Stream.of(COMMAND, EVENT, QUERY, RESULT, SCHEDULE).forEach(type -> {
                    dispatchInterceptors.computeIfPresent(type, (t, i) -> i.andThen(interceptor));
                    handlerDecorators.computeIfPresent(type, (t, i) -> i.andThen(interceptor));
                });
            }

            //enable message correlation
            if (!disableMessageCorrelation) {
                CorrelatingInterceptor correlatingInterceptor = new CorrelatingInterceptor();
                Arrays.stream(MessageType.values()).forEach(
                        type -> dispatchInterceptors.compute(type, (t, i) -> correlatingInterceptor.andThen(i)));
            }

            //enable command and query validation before handling
            if (!disablePayloadValidation) {
                ValidatingInterceptor interceptor = new ValidatingInterceptor();
                Stream.of(COMMAND, QUERY).forEach(type -> handlerDecorators.computeIfPresent(
                        type, (t, i) -> i.andThen(interceptor)));
            }

            //enable scheduling interceptor
            dispatchInterceptors.computeIfPresent(SCHEDULE, (t, i) -> i.andThen(schedulingInterceptor));
            handlerDecorators.computeIfPresent(SCHEDULE, (t, i) -> i.andThen(schedulingInterceptor));

            //collect metrics about consumers and handlers
            if (!disableTrackingMetrics) {
                BatchInterceptor batchInterceptor = new TrackerMonitor();
                HandlerMonitor handlerMonitor = new HandlerMonitor();
                EnumSet.complementOf(EnumSet.of(METRICS)).forEach(type -> {
                    generalBatchInterceptors.computeIfAbsent(type, t -> new ArrayList<>()).add(batchInterceptor);
                    handlerDecorators.compute(type, (t, i) -> handlerMonitor.andThen(i));
                });
            }

            //add customer interceptors
            lowPrioDispatchInterceptors.forEach((messageType, interceptors) -> interceptors.forEach(
                    interceptor -> dispatchInterceptors.computeIfPresent(messageType,
                                                                         (t, i) -> i.andThen(interceptor))));
            highPrioDispatchInterceptors.forEach((messageType, interceptors) -> interceptors.forEach(
                    interceptor -> dispatchInterceptors.computeIfPresent(messageType,
                                                                         (t, i) -> interceptor.andThen(i))));
            lowPrioHandlerDecorators.forEach((messageType, interceptors) -> interceptors.forEach(
                    interceptor -> handlerDecorators.computeIfPresent(messageType,
                                                                      (t, i) -> i.andThen(interceptor))));
            highPrioHandlerDecorators.forEach((messageType, interceptors) -> interceptors.forEach(
                    interceptor -> handlerDecorators.computeIfPresent(messageType,
                                                                      (t, i) -> interceptor.andThen(i))));

            //add websocket dispatch interceptor
            dispatchInterceptors.computeIfPresent(WEBRESPONSE, (t, i) -> new WebsocketResponseInterceptor().andThen(i));

            //add document handler decorator
            AtomicReference<DocumentStore> documentStore = new AtomicReference<>();
            Supplier<DocumentStore> documentStoreSupplier = documentStore::get;
            handlerDecorators.computeIfPresent(
                    DOCUMENT, (t, i) -> new DocumentHandlerDecorator(documentStoreSupplier).andThen(i));

            if (!disableWebResponseCompression) {
                dispatchInterceptors.computeIfPresent(
                        WEBRESPONSE, (t, i) -> new WebResponseCompressingInterceptor().andThen(i));
            }

            if (!disableAdhocDispatchInterceptor) {
                AdhocDispatchInterceptor adhocInterceptor = new AdhocDispatchInterceptor();
                EnumSet.allOf(MessageType.class).forEach(
                        messageType -> dispatchInterceptors.computeIfPresent(messageType,
                                                                             (t, i) -> adhocInterceptor.andThen(i)));
            }

            /*
                Create components
             */

            ResultGateway webResponseGateway = new WebResponseGateway(client.getGatewayClient(WEBRESPONSE),
                                                                      serializer, dispatchInterceptors.get(WEBRESPONSE),
                                                                      webResponseMapper);

            //add websocket request handler decorator
            var websocketHandlerDecorator = new WebsocketHandlerDecorator(webResponseGateway, serializer, taskScheduler);
            handlerDecorators.computeIfPresent(WEBREQUEST, (t, i) -> i.andThen(websocketHandlerDecorator));

            List<ParameterResolver<? super DeserializingMessage>> parameterResolvers =
                    new ArrayList<>(customParameterResolvers);
            if (userProvider != null) {
                parameterResolvers.add(new UserParameterResolver(userProvider));
            }
            parameterResolvers.addAll(List.of(new TriggerParameterResolver(client, serializer),
                                              new DeserializingMessageParameterResolver(),
                                              new MetadataParameterResolver(), new MessageParameterResolver(),
                                              websocketHandlerDecorator,
                                              new WebParamParameterResolver(),
                                              new WebPayloadParameterResolver(
                                                      !disablePayloadValidation, userProvider != null),
                                              new PayloadParameterResolver(),
                                              new EntityParameterResolver()));

            var repositorySupplier = new DefaultRepositoryProvider();
            var handlerRepositorySupplier = DefaultHandlerRepository.handlerRepositorySupplier(documentStoreSupplier,
                                                                                               documentSerializer);

            documentStore.set(new DefaultDocumentStore(
                    client.getSearchClient(), documentSerializer,
                    client.getSearchClient() instanceof InMemorySearchStore searchStore
                            ? new LocalDocumentHandlerRegistry(searchStore, localHandlerRegistry(
                            DOCUMENT, handlerDecorators, parameterResolvers, handlerRepositorySupplier,
                            repositorySupplier), dispatchInterceptors.get(DOCUMENT), serializer)
                            : HandlerRegistry.noOp()));

            //event sourcing
            var entityMatcher = new DefaultEntityHelper(parameterResolvers, disablePayloadValidation);
            EventStore eventStore = new DefaultEventStore(client.getEventStoreClient(), client.getGatewayClient(EVENT),
                                                          serializer, dispatchInterceptors.get(EVENT),
                                                          localHandlerRegistry(EVENT, handlerDecorators,
                                                                               parameterResolvers,
                                                                               handlerRepositorySupplier,
                                                                               repositorySupplier));
            var snapshotStore = new DefaultSnapshotStore(client.getKeyValueClient(), snapshotSerializer, eventStore);

            Cache aggregateCache = new NamedCache(cache, id -> "$Aggregate:" + id);
            AggregateRepository aggregateRepository = new DefaultAggregateRepository(
                    eventStore, client.getEventStoreClient(), snapshotStore, aggregateCache,
                    relationshipsCache, documentStore.get(),
                    serializer, dispatchInterceptors.get(EVENT), entityMatcher);

            if (!disableAutomaticAggregateCaching) {
                aggregateRepository = new CachingAggregateRepository(
                        aggregateRepository, client, aggregateCache, relationshipsCache, this.serializer);
            }


            //create gateways
            RequestHandler defaultRequestHandler = new DefaultRequestHandler(client, RESULT);

            //enable error reporter as the outermost handler interceptor
            ErrorGateway errorGateway =
                    new DefaultErrorGateway(createRequestGateway(client, ERROR, null, defaultRequestHandler,
                                                                 dispatchInterceptors, handlerDecorators,
                                                                 parameterResolvers, handlerRepositorySupplier,
                                                                 repositorySupplier, defaultResponseMapper));
            if (!disableErrorReporting) {
                ErrorReportingInterceptor interceptor = new ErrorReportingInterceptor(errorGateway);
                Arrays.stream(MessageType.values())
                        .forEach(type -> handlerDecorators.compute(type, (t, i) -> interceptor.andThen(i)));
            }

            ResultGateway resultGateway = new DefaultResultGateway(client.getGatewayClient(RESULT),
                                                                   serializer, dispatchInterceptors.get(RESULT),
                                                                   defaultResponseMapper);
            CommandGateway commandGateway =
                    new DefaultCommandGateway(createRequestGateway(client, COMMAND, null, defaultRequestHandler,
                                                                   dispatchInterceptors, handlerDecorators,
                                                                   parameterResolvers, handlerRepositorySupplier,
                                                                   repositorySupplier, defaultResponseMapper));
            QueryGateway queryGateway =
                    new DefaultQueryGateway(createRequestGateway(client, QUERY, null, defaultRequestHandler,
                                                                 dispatchInterceptors, handlerDecorators,
                                                                 parameterResolvers, handlerRepositorySupplier,
                                                                 repositorySupplier, defaultResponseMapper));
            EventGateway eventGateway =
                    new DefaultEventGateway(createRequestGateway(client, EVENT, null, defaultRequestHandler,
                                                                 dispatchInterceptors, handlerDecorators,
                                                                 parameterResolvers, handlerRepositorySupplier,
                                                                 repositorySupplier, defaultResponseMapper));

            MetricsGateway metricsGateway =
                    new DefaultMetricsGateway(createRequestGateway(client, METRICS, null, defaultRequestHandler,
                                                                   dispatchInterceptors, handlerDecorators,
                                                                   parameterResolvers, handlerRepositorySupplier,
                                                                   repositorySupplier, defaultResponseMapper));

            RequestHandler webRequestHandler = new DefaultRequestHandler(client, WEBRESPONSE);
            WebRequestGateway webRequestGateway =
                    new DefaultWebRequestGateway(createRequestGateway(client, WEBREQUEST, null, webRequestHandler,
                                                                      dispatchInterceptors, handlerDecorators,
                                                                      parameterResolvers, handlerRepositorySupplier,
                                                                      repositorySupplier, webResponseMapper));
            Function<String, GenericGateway> customGateways = memoize(topic -> createRequestGateway(
                    client, CUSTOM, topic, defaultRequestHandler, dispatchInterceptors, handlerDecorators,
                    parameterResolvers, handlerRepositorySupplier, repositorySupplier, defaultResponseMapper));


            //tracking
            Map<MessageType, Tracking> trackingMap = stream(MessageType.values())
                    .collect(toMap(identity(), m -> new DefaultTracking(
                            m, m == WEBREQUEST ? webResponseGateway : resultGateway, consumerConfigurations.get(m),
                            generalBatchInterceptors.getOrDefault(m, List.of()), this.serializer,
                            new DefaultHandlerFactory(m, handlerDecorators.get(m == NOTIFICATION ? EVENT : m),
                                                      parameterResolvers, handlerRepositorySupplier,
                                                      repositorySupplier))));

            //misc
            Scheduler scheduler = new DefaultScheduler(client.getSchedulingClient(),
                                                       serializer, dispatchInterceptors.get(SCHEDULE),
                                                       dispatchInterceptors.get(COMMAND),
                                                       localHandlerRegistry(SCHEDULE, handlerDecorators,
                                                                            parameterResolvers,
                                                                            handlerRepositorySupplier,
                                                                            repositorySupplier));

            if (!disableCacheEvictionMetrics) {
                new CacheEvictionsLogger(metricsGateway).register(cache);
            }

            ThrowingRunnable shutdownHandler = () -> {
                ForkJoinPool shutdownPool = new ForkJoinPool(MessageType.values().length);
                Optional.ofNullable(forwardingWebConsumer).ifPresent(ForwardingWebConsumer::close);
                shutdownPool.invokeAll(
                        trackingMap.values().stream()
                                .map(t -> (Callable<?>) () -> {
                                    t.close();
                                    return null;
                                }).collect(toList()));
                shutdownPool.invokeAll(
                        Stream.<Runnable>of(commandGateway::close, queryGateway::close, webRequestGateway::close)
                                .map(t -> (Callable<?>) () -> {
                                    t.run();
                                    return null;
                                }).collect(toList()));
                defaultRequestHandler.close();
                webRequestHandler.close();
                cache.close();
                relationshipsCache.close();
                client.shutDown();
                shutdownPool.close();
            };

            //and finally...
            FluxCapacitor fluxCapacitor =
                    doBuild(trackingMap, customGateways, commandGateway, queryGateway, eventGateway,
                            resultGateway, errorGateway, metricsGateway, webRequestGateway,
                            aggregateRepository, snapshotStore,
                            eventStore, keyValueStore, documentStore.get(), scheduler, userProvider,
                            cache, serializer, correlationDataProvider, identityProvider,
                            propertySource instanceof DecryptingPropertySource dps
                                    ? dps : new DecryptingPropertySource(propertySource),
                            clock, taskScheduler, client, shutdownHandler);

            if (makeApplicationInstance) {
                FluxCapacitor.applicationInstance.set(fluxCapacitor);
            }

            Optional.ofNullable(forwardingWebConsumer).ifPresent(c -> c.start(fluxCapacitor));

            if (!disableScheduledCommandHandler) {
                fluxCapacitor.registerHandlers(new ScheduledCommandHandler());
            }

            //perform a controlled shutdown when the vm exits
            if (!disableShutdownHook) {
                getRuntime().addShutdownHook(
                        new Thread(fluxCapacitor::close, newThreadName("DefaultFluxCapacitor-shutdown")));
            }

            return fluxCapacitor;
        }

        protected FluxCapacitor doBuild(Map<MessageType, ? extends Tracking> trackingSupplier,
                                        Function<String, ? extends GenericGateway> customGatewaySupplier,
                                        CommandGateway commandGateway, QueryGateway queryGateway,
                                        EventGateway eventGateway, ResultGateway resultGateway,
                                        ErrorGateway errorGateway, MetricsGateway metricsGateway,
                                        WebRequestGateway webRequestGateway,
                                        AggregateRepository aggregateRepository, SnapshotStore snapshotStore,
                                        EventStore eventStore, KeyValueStore keyValueStore, DocumentStore documentStore,
                                        Scheduler scheduler, UserProvider userProvider, Cache cache,
                                        Serializer serializer, CorrelationDataProvider correlationDataProvider,
                                        IdentityProvider identityProvider, PropertySource propertySource,
                                        DelegatingClock clock, TaskScheduler taskScheduler,
                                        Client client, ThrowingRunnable shutdownHandler) {
            return new DefaultFluxCapacitor(trackingSupplier, customGatewaySupplier,
                                            commandGateway, queryGateway, eventGateway, resultGateway,
                                            errorGateway, metricsGateway, webRequestGateway,
                                            aggregateRepository, snapshotStore, eventStore,
                                            keyValueStore, documentStore,
                                            scheduler, userProvider, cache, serializer, correlationDataProvider,
                                            identityProvider, propertySource,
                                            clock, taskScheduler, client, shutdownHandler);
        }

        protected ConsumerConfiguration getDefaultConsumerConfiguration(MessageType messageType) {
            return ConsumerConfiguration.builder()
                    .name(messageType.name())
                    .ignoreSegment(messageType == NOTIFICATION)
                    .clientControlledIndex(messageType == NOTIFICATION)
                    .build();
        }

        protected GenericGateway createRequestGateway(Client client, MessageType messageType,
                                                      String topic, RequestHandler requestHandler,
                                                      Map<MessageType, DispatchInterceptor> dispatchInterceptors,
                                                      Map<MessageType, HandlerDecorator> handlerDecorators,
                                                      List<ParameterResolver<? super DeserializingMessage>> parameterResolvers,
                                                      Function<Class<?>, HandlerRepository> handlerRepositorySupplier,
                                                      RepositoryProvider repositorySupplier,
                                                      ResponseMapper responseMapper) {
            return new DefaultGenericGateway(client.getGatewayClient(messageType, topic), requestHandler,
                                             this.serializer, dispatchInterceptors.get(messageType), messageType,
                                             topic, localHandlerRegistry(messageType, handlerDecorators,
                                                                         parameterResolvers, handlerRepositorySupplier,
                                                                         repositorySupplier),
                                             responseMapper);
        }

        protected HandlerRegistry localHandlerRegistry(MessageType messageType,
                                                       Map<MessageType, HandlerDecorator> handlerDecorators,
                                                       List<ParameterResolver<? super DeserializingMessage>> parameterResolvers,
                                                       Function<Class<?>, HandlerRepository> handlerRepositorySupplier,
                                                       RepositoryProvider repositoryProvider) {
            return switch (messageType) {
                case EVENT -> new LocalHandlerRegistry(new DefaultHandlerFactory(
                        messageType, handlerDecorators.get(messageType),
                        parameterResolvers, handlerRepositorySupplier, repositoryProvider))
                        .andThen(new LocalHandlerRegistry(new DefaultHandlerFactory(
                                NOTIFICATION, handlerDecorators.get(EVENT), parameterResolvers,
                                handlerRepositorySupplier, repositoryProvider)));
                default -> new LocalHandlerRegistry(new DefaultHandlerFactory(
                        messageType, handlerDecorators.get(messageType), parameterResolvers,
                        handlerRepositorySupplier, repositoryProvider));
            };
        }
    }

}
