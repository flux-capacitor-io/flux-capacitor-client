/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.search.DocumentSerializer;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.publishing.correlation.CorrelationDataProvider;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import io.fluxcapacitor.javaclient.web.LocalServerConfig;
import io.fluxcapacitor.javaclient.web.WebResponseMapper;

import java.util.function.UnaryOperator;

/**
 * Builder for a Flux Capacitor client instance.
 */
public interface FluxCapacitorBuilder {
    FluxCapacitorBuilder configureDefaultConsumer(MessageType messageType,
                                                  UnaryOperator<ConsumerConfiguration> updateFunction);

    FluxCapacitorBuilder addConsumerConfiguration(ConsumerConfiguration consumerConfiguration);

    FluxCapacitorBuilder addBatchInterceptor(BatchInterceptor interceptor, MessageType... forTypes);

    default FluxCapacitorBuilder addDispatchInterceptor(DispatchInterceptor interceptor, MessageType... forTypes) {
        return addDispatchInterceptor(interceptor, false, forTypes);
    }

    FluxCapacitorBuilder addDispatchInterceptor(DispatchInterceptor interceptor, boolean highPriority, MessageType... forTypes);

    default FluxCapacitorBuilder addHandlerInterceptor(HandlerInterceptor interceptor, MessageType... forTypes) {
        return addHandlerInterceptor(interceptor, false, forTypes);
    }

    FluxCapacitorBuilder addHandlerInterceptor(HandlerInterceptor interceptor, boolean highPriority, MessageType... forTypes);

    FluxCapacitorBuilder replaceMessageRoutingInterceptor(DispatchInterceptor messageRoutingInterceptor);

    FluxCapacitorBuilder replaceCache(Cache cache);

    default FluxCapacitorBuilder forwardWebRequestsToLocalServer(int port) {
        return forwardWebRequestsToLocalServer(LocalServerConfig.builder().port(port).build(), UnaryOperator.identity());
    }

    FluxCapacitorBuilder forwardWebRequestsToLocalServer(LocalServerConfig localServerConfig,
                                                         UnaryOperator<ConsumerConfiguration> consumerConfigurator);

    FluxCapacitorBuilder replaceWebResponseMapper(WebResponseMapper webResponseMapper);

    /**
     * Configures a dedicated cache to use for aggregates of the given type. If no dedicated cache is set aggregates
     * will be stored in the default cache.
     */
    FluxCapacitorBuilder withAggregateCache(Class<?> aggregateType, Cache cache);

    FluxCapacitorBuilder addParameterResolver(ParameterResolver<DeserializingMessage> parameterResolver);

    /**
     * Register a custom serializer. This serializer will also be used for aggregate snapshots unless a custom snapshot
     * serializer is registered using {@link #replaceSnapshotSerializer(Serializer)}. This serializer will also be used
     * as {@link DocumentSerializer} if supported unless a custom document serializer is registered using
     * {@link #replaceDocumentSerializer(DocumentSerializer)}.
     */
    FluxCapacitorBuilder replaceSerializer(Serializer serializer);

    FluxCapacitorBuilder replaceCorrelationDataProvider(CorrelationDataProvider correlationDataProvider);

    FluxCapacitorBuilder replaceSnapshotSerializer(Serializer serializer);

    FluxCapacitorBuilder replaceDocumentSerializer(DocumentSerializer documentSerializer);

    FluxCapacitorBuilder registerUserSupplier(UserProvider userProvider);

    FluxCapacitorBuilder disableErrorReporting();

    FluxCapacitorBuilder disableShutdownHook();

    FluxCapacitorBuilder disableMessageCorrelation();

    FluxCapacitorBuilder disablePayloadValidation();

    FluxCapacitorBuilder disableDataProtection();

    FluxCapacitorBuilder disableAutomaticAggregateCaching();

    FluxCapacitorBuilder enableTrackingMetrics();

    FluxCapacitorBuilder makeApplicationInstance(boolean makeApplicationInstance);

    FluxCapacitor build(Client client);
}
