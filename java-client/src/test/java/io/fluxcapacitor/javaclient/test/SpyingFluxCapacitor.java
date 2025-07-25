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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.TaskScheduler;
import io.fluxcapacitor.common.application.PropertySource;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.IdentityProvider;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorConfiguration;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.SnapshotStore;
import io.fluxcapacitor.javaclient.persisting.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.publishing.CommandGateway;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.publishing.EventGateway;
import io.fluxcapacitor.javaclient.publishing.GenericGateway;
import io.fluxcapacitor.javaclient.publishing.MetricsGateway;
import io.fluxcapacitor.javaclient.publishing.QueryGateway;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.publishing.WebRequestGateway;
import io.fluxcapacitor.javaclient.publishing.correlation.CorrelationDataProvider;
import io.fluxcapacitor.javaclient.scheduling.MessageScheduler;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import lombok.AllArgsConstructor;
import org.mockito.Mockito;

import java.time.Clock;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A {@link FluxCapacitor} implementation that wraps another instance and spies on its major components using Mockito.
 * <p>
 * This class is used internally by {@link io.fluxcapacitor.javaclient.test.TestFixture#spy()} to enable verification
 * of interactions with {@link FluxCapacitor} infrastructure such as gateways, stores, repositories, and schedulers.
 * <p>
 * Components that are wrapped with {@link Mockito#spy(Object)} include:
 * <ul>
 *     <li>{@link AggregateRepository}</li>
 *     <li>{@link CommandGateway}</li>
 *     <li>{@link QueryGateway}</li>
 *     <li>{@link EventGateway}</li>
 *     <li>{@link EventStore}</li>
 *     <li>{@link SnapshotStore}</li>
 *     <li>{@link ResultGateway}</li>
 *     <li>{@link ErrorGateway}</li>
 *     <li>{@link MetricsGateway}</li>
 *     <li>{@link MessageScheduler}</li>
 *     <li>{@link GenericGateway} (via {@code customGateway(...)})</li>
 *     <li>{@link Tracking}</li>
 *     <li>{@link KeyValueStore}</li>
 *     <li>{@link DocumentStore}</li>
 *     <li>{@link Cache}</li>
 * </ul>
 * Other methods delegate directly to the original (non-spied) {@link FluxCapacitor} instance.
 *
 * @see SpyingClient
 */
@AllArgsConstructor
public class SpyingFluxCapacitor implements FluxCapacitor {

    /** Weakly-held cache of all spy-decorated components. */
    private final Map<Object, Object> spiedComponents = new WeakHashMap<>();

    /** The actual underlying {@link FluxCapacitor} instance. */
    private final FluxCapacitor delegate;

    /**
     * Wraps a component in a spy, or returns the previously created spy from the cache.
     *
     * @param component the component to spy
     * @param <T>       the type of the component
     * @return the spied version of the component
     */
    @SuppressWarnings("unchecked")
    protected <T> T decorate(T component) {
        return (T) spiedComponents.computeIfAbsent(component, Mockito::spy);
    }

    /**
     * Resets all previously created spies, clearing any captured interactions or stubbing.
     */
    public void resetMocks() {
        spiedComponents.values().forEach(Mockito::reset);
    }

    @Override
    public AggregateRepository aggregateRepository() {
        return decorate(delegate.aggregateRepository());
    }

    @Override
    public EventStore eventStore() {
        return decorate(delegate.eventStore());
    }

    @Override
    public SnapshotStore snapshotStore() {
        return decorate(delegate.snapshotStore());
    }

    @Override
    public MessageScheduler messageScheduler() {
        return decorate(delegate.messageScheduler());
    }

    @Override
    public CommandGateway commandGateway() {
        return decorate(delegate.commandGateway());
    }

    @Override
    public QueryGateway queryGateway() {
        return decorate(delegate.queryGateway());
    }

    @Override
    public EventGateway eventGateway() {
        return decorate(delegate.eventGateway());
    }

    @Override
    public ResultGateway resultGateway() {
        return decorate(delegate.resultGateway());
    }

    @Override
    public ErrorGateway errorGateway() {
        return decorate(delegate.errorGateway());
    }

    @Override
    public MetricsGateway metricsGateway() {
        return decorate(delegate.metricsGateway());
    }

    @Override
    public GenericGateway customGateway(String topic) {
        return decorate(delegate.customGateway(topic));
    }

    @Override
    public Tracking tracking(MessageType messageType) {
        return decorate(delegate.tracking(messageType));
    }

    @Override
    public KeyValueStore keyValueStore() {
        return decorate(delegate.keyValueStore());
    }

    @Override
    public DocumentStore documentStore() {
        return decorate(delegate.documentStore());
    }

    @Override
    public Cache cache() {
        return decorate(delegate.cache());
    }

    @Override
    public WebRequestGateway webRequestGateway() {
        return delegate.webRequestGateway(); // intentionally not spied
    }

    @Override
    public void withClock(Clock clock) {
        delegate.withClock(clock);
    }

    @Override
    public UserProvider userProvider() {
        return delegate.userProvider();
    }

    @Override
    public CorrelationDataProvider correlationDataProvider() {
        return delegate.correlationDataProvider();
    }

    @Override
    public Serializer serializer() {
        return delegate.serializer();
    }

    @Override
    public Clock clock() {
        return delegate.clock();
    }

    @Override
    public IdentityProvider identityProvider() {
        return delegate.identityProvider();
    }

    @Override
    public PropertySource propertySource() {
        return delegate.propertySource();
    }

    @Override
    public TaskScheduler taskScheduler() {
        return delegate.taskScheduler();
    }

    @Override
    public FluxCapacitorConfiguration configuration() {
        return delegate.configuration();
    }

    @Override
    public Client client() {
        return delegate.client();
    }

    @Override
    public Registration beforeShutdown(Runnable task) {
        return delegate.beforeShutdown(task);
    }

    @Override
    public void close(boolean silently) {
        delegate.close(silently);
    }
}
