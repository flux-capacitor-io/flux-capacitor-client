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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.TaskScheduler;
import io.fluxcapacitor.common.application.PropertySource;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.IdentityProvider;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.search.DocumentSerializer;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.publishing.correlation.CorrelationDataProvider;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerDecorator;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.ResponseMapper;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import io.fluxcapacitor.javaclient.web.LocalServerConfig;
import io.fluxcapacitor.javaclient.web.WebResponseMapper;

import java.time.Clock;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Builder interface for constructing a {@link FluxCapacitor} instance.
 * <p>
 * This interface exposes advanced configuration hooks for customizing message handling, dispatch behavior,
 * serialization, user resolution, correlation tracking, and many other aspects of a Flux Capacitor client. It is
 * primarily used via {@link io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor} but can be extended or
 * wrapped for deeper integrations.
 */
public interface FluxCapacitorBuilder {

    /**
     * Update the default consumer configuration for the specified message type.
     */
    FluxCapacitorBuilder configureDefaultConsumer(MessageType messageType,
                                                  UnaryOperator<ConsumerConfiguration> updateFunction);

    /**
     * Adds a specific consumer configuration for one or more message types.
     */
    FluxCapacitorBuilder addConsumerConfiguration(ConsumerConfiguration consumerConfiguration,
                                                  MessageType... messageTypes);

    /**
     * Registers a {@link BatchInterceptor} that applies to the given message types.
     */
    FluxCapacitorBuilder addBatchInterceptor(BatchInterceptor interceptor, MessageType... forTypes);

    /**
     * Adds a {@link DispatchInterceptor} that modifies or monitors message dispatch. Shortcut for highPriority =
     * false.
     */
    default FluxCapacitorBuilder addDispatchInterceptor(DispatchInterceptor interceptor, MessageType... forTypes) {
        return addDispatchInterceptor(interceptor, false, forTypes);
    }

    /**
     * Adds a {@link DispatchInterceptor} for specified message types with optional priority.
     */
    FluxCapacitorBuilder addDispatchInterceptor(DispatchInterceptor interceptor, boolean highPriority,
                                                MessageType... forTypes);

    /**
     * Adds a {@link HandlerInterceptor} for given message types.
     */
    default FluxCapacitorBuilder addHandlerInterceptor(HandlerInterceptor interceptor, MessageType... forTypes) {
        return addHandlerDecorator(interceptor, forTypes);
    }

    /**
     * Adds a {@link HandlerInterceptor} with specified priority.
     */
    default FluxCapacitorBuilder addHandlerInterceptor(HandlerInterceptor interceptor, boolean highPriority,
                                                       MessageType... forTypes) {
        return addHandlerDecorator(interceptor, highPriority, forTypes);
    }

    /**
     * Adds a {@link HandlerDecorator} for the given message types.
     */
    default FluxCapacitorBuilder addHandlerDecorator(HandlerDecorator decorator, MessageType... forTypes) {
        return addHandlerDecorator(decorator, false, forTypes);
    }

    /**
     * Adds a {@link HandlerDecorator} with control over priority.
     */
    FluxCapacitorBuilder addHandlerDecorator(HandlerDecorator decorator, boolean highPriority, MessageType... forTypes);

    /**
     * Replaces the default routing interceptor used for message dispatch.
     */
    FluxCapacitorBuilder replaceMessageRoutingInterceptor(DispatchInterceptor messageRoutingInterceptor);

    /**
     * Replaces the default cache implementation.
     */
    FluxCapacitorBuilder replaceCache(Cache cache);

    /**
     * Forwards incoming {@link io.fluxcapacitor.common.MessageType#WEBREQUEST} messages to a locally running HTTP
     * server on the specified port.
     * <p>
     * This allows applications to handle web requests using their own HTTP server rather than Flux Capacitor’s
     * message-based {@code @HandleWeb} infrastructure.
     * <p>
     * <strong>Note:</strong> This feature pushes requests to the local server and bypasses Flux’s pull-based
     * dispatch model. Its use is discouraged unless integration with an existing HTTP stack is required.
     *
     * @param port the port on which the local HTTP server is listening
     * @return this builder instance
     * @see #forwardWebRequestsToLocalServer(LocalServerConfig, UnaryOperator)
     * @see io.fluxcapacitor.javaclient.web.ForwardingWebConsumer
     */
    default FluxCapacitorBuilder forwardWebRequestsToLocalServer(int port) {
        return forwardWebRequestsToLocalServer(LocalServerConfig.builder().port(port).build(),
                                               UnaryOperator.identity());
    }

    /**
     * Configures forwarding of {@link io.fluxcapacitor.common.MessageType#WEBREQUEST} messages to a local HTTP server
     * using the specified {@link LocalServerConfig} and custom consumer configuration.
     * <p>
     * This mechanism is useful for advanced integration scenarios but bypasses Flux's pull-based message tracking.
     * Prefer native {@code @HandleWeb} handlers when possible.
     *
     * @param localServerConfig    configuration for the local server (e.g., port, error behavior)
     * @param consumerConfigurator function to customize the underlying
     *                             {@link io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration}
     * @return this builder instance
     * @see io.fluxcapacitor.javaclient.web.ForwardingWebConsumer
     */
    FluxCapacitorBuilder forwardWebRequestsToLocalServer(LocalServerConfig localServerConfig,
                                                         UnaryOperator<ConsumerConfiguration> consumerConfigurator);

    /**
     * Replaces the default response mapper used for generic result mapping.
     */
    FluxCapacitorBuilder replaceDefaultResponseMapper(ResponseMapper responseMapper);

    /**
     * Replaces the {@link WebResponseMapper} used for handling web responses.
     */
    FluxCapacitorBuilder replaceWebResponseMapper(WebResponseMapper webResponseMapper);

    /**
     * Replaces the default {@link TaskScheduler} implementation.
     */
    FluxCapacitorBuilder replaceTaskScheduler(Function<Clock, TaskScheduler> function);

    /**
     * Configures a dedicated cache for a specific aggregate type.
     */
    FluxCapacitorBuilder withAggregateCache(Class<?> aggregateType, Cache cache);

    /**
     * Replaces the internal relationships cache with a new implementation.
     */
    FluxCapacitorBuilder replaceRelationshipsCache(UnaryOperator<Cache> replaceFunction);

    /**
     * Replaces the identity provider used to generate message and entity identifiers.
     */
    FluxCapacitorBuilder replaceIdentityProvider(UnaryOperator<IdentityProvider> replaceFunction);

    /**
     * Registers a {@link ParameterResolver} to support injection of method arguments in handlers.
     */
    FluxCapacitorBuilder addParameterResolver(ParameterResolver<? super DeserializingMessage> parameterResolver);

    /**
     * Replaces the default serializer used for events, commands, snapshots, and documents.
     */
    FluxCapacitorBuilder replaceSerializer(Serializer serializer);

    /**
     * Replaces the {@link CorrelationDataProvider} used to attach correlation data to messages.
     */
    FluxCapacitorBuilder replaceCorrelationDataProvider(UnaryOperator<CorrelationDataProvider> correlationDataProvider);

    /**
     * Overrides the serializer used specifically for snapshot serialization.
     */
    FluxCapacitorBuilder replaceSnapshotSerializer(Serializer serializer);

    /**
     * Replaces the document serializer for search indexing.
     */
    FluxCapacitorBuilder replaceDocumentSerializer(DocumentSerializer documentSerializer);

    /**
     * Registers a user provider used for resolving and authenticating {@link User} instances.
     */
    FluxCapacitorBuilder registerUserProvider(UserProvider userProvider);

    /**
     * Adds a {@link PropertySource} to the configuration chain.
     */
    default FluxCapacitorBuilder addPropertySource(PropertySource propertySource) {
        return replacePropertySource(existing -> existing.andThen(propertySource));
    }

    /**
     * Replaces the existing property source.
     */
    FluxCapacitorBuilder replacePropertySource(UnaryOperator<PropertySource> replacer);

    /**
     * Disables automatic error reporting (e.g., via {@link ErrorGateway}).
     */
    FluxCapacitorBuilder disableErrorReporting();

    /**
     * Prevents registration of a shutdown hook.
     */
    FluxCapacitorBuilder disableShutdownHook();

    /**
     * Disables automatic message correlation.
     */
    FluxCapacitorBuilder disableMessageCorrelation();

    /**
     * Disables payload validation.
     */
    FluxCapacitorBuilder disablePayloadValidation();

    /**
     * Disables security filtering based on {@code @FilterContent}.
     */
    FluxCapacitorBuilder disableDataProtection();

    /**
     * Disables automatic caching of aggregates.
     */
    FluxCapacitorBuilder disableAutomaticAggregateCaching();

    /**
     * Prevents installation of the default scheduled command handler.
     */
    FluxCapacitorBuilder disableScheduledCommandHandler();

    /**
     * Disables tracking of processing metrics.
     */
    FluxCapacitorBuilder disableTrackingMetrics();

    /**
     * Disables metrics related to cache eviction.
     */
    FluxCapacitorBuilder disableCacheEvictionMetrics();

    /**
     * Disables compression for web responses.
     */
    FluxCapacitorBuilder disableWebResponseCompression();

    /**
     * Disables support for dynamically injected dispatch interceptors.
     */
    FluxCapacitorBuilder disableAdhocDispatchInterceptor();

    /**
     * Marks the built instance as the global (application-level) {@link FluxCapacitor}.
     */
    FluxCapacitorBuilder makeApplicationInstance(boolean makeApplicationInstance);

    /**
     * Builds the FluxCapacitor instance using the provided low-level {@link Client}.
     */
    FluxCapacitor build(Client client);
}
