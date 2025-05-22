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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;

/**
 * Gateway for publishing event messages to Flux Capacitor.
 * <p>
 * Events represent facts that have occurred and are typically used for downstream processing, projections, or
 * integration with other systems. Unlike commands and queries, events are not expected to produce a response.
 * <p>
 * This gateway supports both local and remote event publishing. If local handlers are registered (via
 * {@link #registerHandler(Object)}), those will be invoked in-process. Otherwise, or in addition, the events are
 * published to the Flux platform for delivery to interested consumers.
 * <p>
 * Note that publishing an event via this gateway does <em>not</em> associate it with an aggregate. If you're
 * implementing an event-sourced aggregate, use {@link io.fluxcapacitor.javaclient.modeling.Entity#apply} instead to
 * apply domain events to the aggregate and persist them in the event store.
 *
 * @see HasLocalHandlers for registering local event handlers
 * @see io.fluxcapacitor.javaclient.modeling.Entity for applying events to aggregates
 * @see io.fluxcapacitor.javaclient.tracking.handling.HandleEvent for handling events
 */
public interface EventGateway extends HasLocalHandlers {

    /**
     * Publishes the given event object. If the object is not already a {@link Message}, it will be wrapped in one. The
     * event is published with no delivery guarantee.
     * <p>
     * If local handlers are registered, they will be invoked synchronously.
     *
     * @param event the event object to publish
     * @throws RuntimeException if an error occurs during publishing
     */
    @SneakyThrows
    default void publish(Object event) {
        publish(Message.asMessage(event), Guarantee.NONE).get();
    }

    /**
     * Publishes an event with the specified payload and metadata. The event is wrapped into a {@link Message}. The
     * event is published with no delivery guarantee.
     * <p>
     * If local handlers are registered, they will be invoked synchronously.
     *
     * @param payload  the event payload
     * @param metadata the associated metadata
     * @throws RuntimeException if an error occurs during publishing
     */
    @SneakyThrows
    default void publish(Object payload, Metadata metadata) {
        publish(new Message(payload, metadata), Guarantee.NONE).get();
    }

    /**
     * Publishes the given {@link Message} to Flux Capacitor and/or local handlers. Returns a future that completes when
     * the message has been fully processed or stored according to the given {@link Guarantee}.
     *
     * @param message   the message to publish
     * @param guarantee the level of delivery guarantee to apply (e.g., store-before-acknowledge)
     * @return a future that completes when the message has been handled
     */
    CompletableFuture<Void> publish(Message message, Guarantee guarantee);

    /**
     * Publishes one or more event messages. Each message may be a raw payload or a {@link Message} instance. Events are
     * published with no delivery guarantee.
     * <p>
     * This method does not block for completion or acknowledgments.
     *
     * @param messages the events to publish
     */
    void publish(Object... messages);

    /**
     * Publishes one or more event messages with a specific delivery guarantee. Each message may be a raw payload or a
     * {@link Message} instance.
     * <p>
     * Returns a future that completes when the messages have been handled or stored according to the specified
     * {@link Guarantee}.
     *
     * @param guarantee the delivery guarantee (e.g., {@code Guarantee.STORED})
     * @param messages  the events to publish
     * @return a future that completes upon publishing
     */
    CompletableFuture<Void> publish(Guarantee guarantee, Object... messages);
}
