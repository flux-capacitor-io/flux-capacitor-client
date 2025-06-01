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
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.ClientDispatchMonitor;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.CachingTrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mockito;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A {@link Client} implementation that wraps another client and spies on its components using Mockito.
 * <p>
 * This class is used internally by {@link io.fluxcapacitor.javaclient.test.TestFixture#spy()} to enable mocking or
 * verification of Flux internal components such as gateway clients, tracking clients, and stores.
 * <p>
 * All supported components are decorated using {@link Mockito#spy(Object)} on first access and cached via a
 * {@link WeakHashMap}. Spy state can be reset via {@link #resetMocks()}.
 */
@Slf4j
@AllArgsConstructor
public class SpyingClient implements Client {

    /**
     * Weakly-held cache of spied components to prevent memory leaks.
     */
    private final Map<Object, Object> spiedComponents = new WeakHashMap<>();

    /**
     * The real client that provides the actual behavior.
     */
    private final Client delegate;

    /**
     * Returns the unwrapped (non-spied) delegate client.
     */
    @Override
    public Client unwrap() {
        return delegate.unwrap();
    }

    /**
     * Decorates a given component by wrapping it in a Mockito spy, or returns an existing spy if already created.
     *
     * @param component the original component to be spied on
     * @param <T>       the type of the component
     * @return the spied component
     */
    @SuppressWarnings("unchecked")
    protected <T> T decorate(T component) {
        return (T) spiedComponents.computeIfAbsent(component, Mockito::spy);
    }

    /**
     * Resets all cached spies to their original state.
     * <p>
     * This clears any recorded interactions and stubbings.
     */
    public void resetMocks() {
        spiedComponents.values().forEach(Mockito::reset);
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public String applicationId() {
        return delegate.applicationId();
    }

    /**
     * Returns a spied version of the gateway client for the given message type and topic.
     */
    @Override
    public GatewayClient getGatewayClient(MessageType messageType, String topic) {
        return decorate(delegate.getGatewayClient(messageType, topic));
    }

    /**
     * Forwards the given monitor to the delegate.
     */
    @Override
    public Registration monitorDispatch(ClientDispatchMonitor monitor, MessageType... messageTypes) {
        return delegate.monitorDispatch(monitor, messageTypes);
    }

    /**
     * Returns a spied version of the tracking client for the given message type and topic.
     * <p>
     * If the returned client is a {@link CachingTrackingClient}, the underlying delegate is also replaced with a spy.
     */
    @Override
    public TrackingClient getTrackingClient(MessageType messageType, String topic) {
        var component = delegate.getTrackingClient(messageType, topic);
        if (component instanceof CachingTrackingClient && !spiedComponents.containsKey(component)) {
            ReflectionUtils.setField("delegate", component,
                                     decorate(((CachingTrackingClient) component).getDelegate()));
        }
        return decorate(component);
    }

    /**
     * Returns a spied version of the event store client.
     */
    @Override
    public EventStoreClient getEventStoreClient() {
        return decorate(delegate.getEventStoreClient());
    }

    /**
     * Returns a spied version of the scheduling client.
     */
    @Override
    public SchedulingClient getSchedulingClient() {
        return decorate(delegate.getSchedulingClient());
    }

    /**
     * Returns a spied version of the key-value client.
     */
    @Override
    public KeyValueClient getKeyValueClient() {
        return decorate(delegate.getKeyValueClient());
    }

    /**
     * Returns a spied version of the search client.
     */
    @Override
    public SearchClient getSearchClient() {
        return decorate(delegate.getSearchClient());
    }

    /**
     * Delegates shutdown to the underlying client.
     */
    @Override
    public void shutDown() {
        delegate.shutDown();
    }

    /**
     * Delegates registration of shutdown hook to the underlying client.
     */
    @Override
    public Registration beforeShutdown(Runnable task) {
        return delegate.beforeShutdown(task);
    }
}