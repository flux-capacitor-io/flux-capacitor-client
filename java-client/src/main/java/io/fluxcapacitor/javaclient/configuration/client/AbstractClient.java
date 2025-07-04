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

package io.fluxcapacitor.javaclient.configuration.client;

import io.fluxcapacitor.common.MemoizingBiFunction;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import static io.fluxcapacitor.common.ObjectUtils.asConsumer;
import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static java.util.Objects.requireNonNull;

/**
 * Abstract base implementation of the {@link Client} interface, providing shared logic and lifecycle management for
 * both local (in-memory) and remote (WebSocket-based) Flux clients.
 * <p>
 * Concrete implementations such as {@link LocalClient} and {@link WebSocketClient} extend this class to define specific
 * transport and storage mechanisms for each subsystem (event store, scheduling, etc.).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *     <li>Manages memoized creation and reuse of {@link GatewayClient} and {@link TrackingClient} instances.</li>
 *     <li>Coordinates shutdown behavior and cleanup tasks via {@link #shutDown()} and {@link #beforeShutdown(Runnable)}.</li>
 *     <li>Provides monitoring support for dispatched messages using {@link ClientDispatchMonitor}.</li>
 * </ul>
 */
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public abstract class AbstractClient implements Client {

    /**
     * Map of message type to a list of dispatch monitors and their corresponding registrations.
     */
    private final Map<MessageType, List<Map.Entry<ClientDispatchMonitor, Registration>>> monitors =
            new ConcurrentHashMap<>();

    /**
     * Lazily instantiated and memoized gateway clients, keyed by message type and topic.
     */
    MemoizingBiFunction<MessageType, String, ? extends GatewayClient> gatewayClients =
            memoize(this::createGatewayClient);

    /**
     * Lazily instantiated and memoized tracking clients, keyed by message type and topic.
     */
    MemoizingBiFunction<MessageType, String, ? extends TrackingClient> trackingClients =
            memoize(this::createTrackingClient);

    @Getter(lazy = true)
    EventStoreClient eventStoreClient = createEventStoreClient();

    @Getter(lazy = true)
    SchedulingClient schedulingClient = createSchedulingClient();

    @Getter(lazy = true)
    KeyValueClient keyValueClient = createKeyValueClient();

    @Getter(lazy = true)
    SearchClient searchClient = createSearchClient();

    /**
     * Tracks shutdown callbacks that will be run when {@link #shutDown()} is invoked.
     */
    protected final Set<Runnable> shutdownTasks = new CopyOnWriteArraySet<>();

    /**
     * Subclasses must implement this method to return a {@link GatewayClient} for the given message type and topic.
     */
    protected abstract GatewayClient createGatewayClient(MessageType messageType, String topic);

    /**
     * Subclasses must implement this method to return a {@link TrackingClient} for the given message type and topic.
     */
    protected abstract TrackingClient createTrackingClient(MessageType messageType, String topic);

    /**
     * Subclasses must implement this method to return a {@link EventStoreClient}.
     */
    protected abstract EventStoreClient createEventStoreClient();

    /**
     * Subclasses must implement this method to return a {@link SchedulingClient}.
     */
    protected abstract SchedulingClient createSchedulingClient();

    /**
     * Subclasses must implement this method to return a {@link KeyValueClient}.
     */
    protected abstract KeyValueClient createKeyValueClient();

    /**
     * Subclasses must implement this method to return a {@link SearchClient}.
     */
    protected abstract SearchClient createSearchClient();

    /**
     * Returns the memoized {@link GatewayClient} for the given message type and topic. Automatically registers any
     * previously registered {@link ClientDispatchMonitor} instances.
     */
    @Override
    public GatewayClient getGatewayClient(MessageType messageType, String topic) {
        switch (messageType) {
            case DOCUMENT, CUSTOM -> requireNonNull(topic);
            default -> {
                if (topic != null) {
                    throw new IllegalArgumentException("Topic is not supported for message type: " + messageType);
                }
            }
        }
        if (!gatewayClients.isCached(messageType, topic)) {
            synchronized (gatewayClients) {
                if (!gatewayClients.isCached(messageType, topic)) {
                    GatewayClient result = gatewayClients.apply(messageType, topic);
                    monitors.getOrDefault(messageType, Collections.emptyList()).forEach(entry -> entry.setValue(
                            result.registerMonitor(messages -> entry.getKey().accept(messageType, topic, messages))));
                    return result;
                }
            }
        }
        return gatewayClients.apply(messageType, topic);
    }

    @Override
    public Registration monitorDispatch(ClientDispatchMonitor monitor, MessageType... messageTypes) {
        if (messageTypes.length == 0) {
            messageTypes = MessageType.values();
        }
        return Arrays.stream(messageTypes).<Registration>map(t -> {
            var list = monitors.computeIfAbsent(t, (k) -> new CopyOnWriteArrayList<>());
            Map.Entry<ClientDispatchMonitor, Registration> entry = new SimpleEntry<>(monitor, null);
            list.add(entry);
            return () -> {
                list.remove(entry);
                Optional.ofNullable(entry.getValue()).ifPresent(Registration::cancel);
            };
        }).reduce(Registration::merge).orElseGet(Registration::noOp);
    }

    @Override
    public TrackingClient getTrackingClient(MessageType messageType, String topic) {
        switch (messageType) {
            case DOCUMENT, CUSTOM -> requireNonNull(topic);
            default -> topic = null;
        }
        return trackingClients.apply(messageType, topic);
    }

    @Override
    public void shutDown() {
        shutdownTasks.forEach(ObjectUtils::tryRun);
        trackingClients.forEach(asConsumer(AutoCloseable::close));
        gatewayClients.forEach(asConsumer(AutoCloseable::close));
        getEventStoreClient().close();
        getSchedulingClient().close();
        getKeyValueClient().close();
        getSearchClient().close();
    }

    @Override
    public Registration beforeShutdown(Runnable task) {
        shutdownTasks.add(task);
        return () -> shutdownTasks.remove(task);
    }
}
