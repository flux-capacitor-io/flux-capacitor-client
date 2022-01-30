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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.persisting.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.publishing.CommandGateway;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.publishing.EventGateway;
import io.fluxcapacitor.javaclient.publishing.MetricsGateway;
import io.fluxcapacitor.javaclient.publishing.QueryGateway;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import org.mockito.Mockito;

import java.util.Map;
import java.util.WeakHashMap;

@AllArgsConstructor
public class TestFluxCapacitor implements FluxCapacitor {
    private final Map<Object, Object> spiedComponents = new WeakHashMap<>();

    @Delegate(excludes = ExcludedMethods.class)
    private final FluxCapacitor delegate;

    @SuppressWarnings("unchecked")
    protected <T> T decorate(T component) {
        return (T) spiedComponents.computeIfAbsent(component, Mockito::spy);
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
    public Scheduler scheduler() {
        return decorate(delegate.scheduler());
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

    private interface ExcludedMethods {
        AggregateRepository aggregateRepository();
        EventStore eventStore();
        Scheduler scheduler();
        CommandGateway commandGateway();
        QueryGateway queryGateway();
        EventGateway eventGateway();
        ResultGateway resultGateway();
        ErrorGateway errorGateway();
        MetricsGateway metricsGateway();
        Tracking tracking(MessageType messageType);
        KeyValueStore keyValueStore();
        DocumentStore documentStore();
        Cache cache();
    }

}
