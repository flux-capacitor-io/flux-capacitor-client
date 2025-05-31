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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;

/**
 * Defines the low-level client contract used by {@link FluxCapacitorBuilder} to construct a
 * {@link io.fluxcapacitor.javaclient.FluxCapacitor} instance.
 * <p>
 * Application developers must provide an implementation of this interface to the
 * {@link FluxCapacitorBuilder#build(Client)} method. This allows for flexible configuration, enabling either an
 * in-memory setup (useful for testing and local development) or a connected (WebSocket-based) client that integrates
 * with the Flux Platform.
 *
 * <h2>Client Implementations</h2>
 * Common implementations include:
 * <ul>
 *   <li>{@link LocalClient} — for local, standalone use</li>
 *   <li>{@link WebSocketClient} — connects to the Flux Platform using WebSockets</li>
 * </ul>
 *
 * <h2>Responsibilities</h2>
 * A {@code Client} implementation is responsible for:
 * <ul>
 *   <li>Providing access to gateway and tracking subsystems via {@link GatewayClient} and {@link TrackingClient}</li>
 *   <li>Managing subsystems for event storage, scheduling, key-value access, and search</li>
 *   <li>Handling client shutdown via {@link #shutDown()} and shutdown hooks</li>
 *   <li>Optionally monitoring dispatches via {@link #monitorDispatch(ClientDispatchMonitor, MessageType...)}</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Client client = LocalClient.newInstance();
 * FluxCapacitor fluxCapacitor = FluxCapacitorBuilder.newInstance().build(client);
 * }</pre>
 */
public interface Client {

    /**
     * Returns the name of this client as defined in its configuration.
     */
    String name();

    /**
     * Returns the unique identifier of this client instance. This id may be randomly generated.
     */
    String id();

    /**
     * Returns the application ID under which this client instance is registered.
     */
    String applicationId();

    /**
     * Returns a {@link GatewayClient} for the given message type using the default topic (typically {@code null}).
     * <p>
     * For {@link MessageType#DOCUMENT} or {@link MessageType#CUSTOM}, this method is not supported and will throw an
     * {@link UnsupportedOperationException}, as these require a topic.
     *
     * @param messageType the type of message gateway
     * @return the associated {@link GatewayClient}
     * @throws UnsupportedOperationException if the message type requires a topic
     */
    default GatewayClient getGatewayClient(MessageType messageType) {
        switch (messageType) {
            case DOCUMENT, CUSTOM -> throw new UnsupportedOperationException("Topic is required");
        }
        return getGatewayClient(messageType, null);
    }

    /**
     * Returns a {@link GatewayClient} for the given message type and topic.
     *
     * @param messageType the type of message (e.g. COMMAND, EVENT, etc.)
     * @param topic       the topic to publish messages to (may be {@code null} for default)
     */
    GatewayClient getGatewayClient(MessageType messageType, String topic);

    /**
     * Registers a {@link ClientDispatchMonitor} to receive hooks and diagnostics when messages of the given
     * {@link MessageType}s are dispatched from this client.
     *
     * @param monitor      the dispatch monitor to register
     * @param messageTypes the message types to monitor
     * @return a {@link Registration} handle to remove the monitor
     */
    Registration monitorDispatch(ClientDispatchMonitor monitor, MessageType... messageTypes);

    /**
     * Returns a {@link TrackingClient} for the given message type using the default topic.
     * <p>
     * For {@link MessageType#DOCUMENT} or {@link MessageType#CUSTOM}, this method is not supported and will throw an
     * {@link UnsupportedOperationException}.
     *
     * @param messageType the type of message to track
     * @return the associated {@link TrackingClient}
     * @throws UnsupportedOperationException if the message type requires a topic
     */
    default TrackingClient getTrackingClient(MessageType messageType) {
        switch (messageType) {
            case DOCUMENT, CUSTOM -> throw new UnsupportedOperationException("Topic is required");
        }
        return getTrackingClient(messageType, null);
    }

    /**
     * Returns a {@link TrackingClient} for the given message type and topic.
     *
     * @param messageType the type of message to track (e.g. COMMAND, EVENT, QUERY)
     * @param topic       the topic to track messages from (may be {@code null} for default)
     */
    TrackingClient getTrackingClient(MessageType messageType, String topic);

    /**
     * Returns the {@link EventStoreClient} associated with this client for querying event logs.
     */
    EventStoreClient getEventStoreClient();

    /**
     * Returns the {@link SchedulingClient} used to interact with the Flux scheduling subsystem.
     */
    SchedulingClient getSchedulingClient();

    /**
     * Returns the {@link KeyValueClient} for key-value store interactions.
     * <p>
     * This is mostly deprecated and maintained for backward compatibility.
     */
    KeyValueClient getKeyValueClient();

    /**
     * Returns the {@link SearchClient} that provides access to document and search APIs.
     */
    SearchClient getSearchClient();

    /**
     * Shuts down this client instance, releasing any underlying resources.
     * <p>
     * This includes closing websocket sessions, stopping tracking, and executing registered shutdown hooks.
     */
    void shutDown();

    /**
     * Registers a shutdown hook that will be called before this client shuts down.
     *
     * @param task the action to invoke before shutdown
     * @return a {@link Registration} to remove the task
     */
    Registration beforeShutdown(Runnable task);

    /**
     * Returns the underlying {@code Client} implementation. This is a convenience method to allow clients to explicitly
     * unwrap proxies or decorators.
     *
     * @return the concrete {@code Client} instance (often {@code this} itself)
     */
    default Client unwrap() {
        return this;
    }
}
