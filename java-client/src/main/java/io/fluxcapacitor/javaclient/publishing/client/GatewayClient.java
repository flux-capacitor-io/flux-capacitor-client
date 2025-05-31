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

package io.fluxcapacitor.javaclient.publishing.client;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.Monitored;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.publishing.CommandGateway;
import io.fluxcapacitor.javaclient.publishing.EventGateway;
import io.fluxcapacitor.javaclient.publishing.QueryGateway;
import io.fluxcapacitor.javaclient.publishing.WebRequestGateway;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Low-level interface for sending {@link SerializedMessage}s to a gateway (e.g. commands, events, queries).
 * <p>
 * This interface is primarily used internally within the Flux Capacitor client and its platform transport layers. It
 * defines the mechanics for appending serialized messages to a backing system such as the Flux platform or an in-memory
 * store.
 *
 * <p><strong>Note:</strong> End users typically <em>do not interact</em> with this interface directly.
 * Instead, they should rely on the higher-level gateways such as:
 * <ul>
 *   <li>{@link CommandGateway}</li>
 *   <li>{@link QueryGateway}</li>
 *   <li>{@link EventGateway}</li>
 *   <li>{@link WebRequestGateway}</li>
 * </ul>
 * or more conveniently, dispatch messages using the static utility methods provided by {@link FluxCapacitor},
 * such as:
 * <pre>
 *     FluxCapacitor.sendCommandAndWait(new MyCommand());
 *     FluxCapacitor.publishEvent(new SomethingHappened());
 * </pre>
 *
 * @see SerializedMessage
 * @see Guarantee
 */
public interface GatewayClient extends AutoCloseable, Monitored<List<SerializedMessage>> {

    /**
     * Append the given messages to the gateway, applying the given delivery {@link Guarantee}.
     *
     * @param guarantee the delivery guarantee that should be respected (e.g. at-most-once, at-least-once)
     * @param messages  one or more serialized messages to append
     * @return a {@link CompletableFuture} that completes when the append operation is successful or fails if delivery
     * fails
     */
    CompletableFuture<Void> append(Guarantee guarantee, SerializedMessage... messages);

    /**
     * Set a new retention duration for the underlying gateway's message log.
     * <p>
     * The retention setting determines how long messages in this log are retained by the system, after which they may
     * be evicted or deleted depending on the platform policy.
     *
     * @param duration  the new retention duration
     * @param guarantee the delivery guarantee to apply to the update operation
     * @return a {@link CompletableFuture} that completes once the retention setting is updated
     */
    CompletableFuture<Void> setRetentionTime(Duration duration, Guarantee guarantee);

    /**
     * Closes this gateway client, releasing any associated resources (e.g. network connections or background tasks).
     */
    @Override
    void close();
}
