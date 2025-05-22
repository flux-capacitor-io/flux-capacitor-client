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
 * Gateway interface for publishing metrics messages in Flux Capacitor.
 * <p>
 * This gateway provides a convenient way to log and transmit application performance data, operational metrics, or
 * monitoring signals. Metrics messages can be published either as raw payloads or as fully constructed {@link Message}
 * objects. Metadata and delivery guarantees can also be specified.
 * <p>
 * Metrics are typically not routed for domain logic handling, but can be consumed by local handlers as well as
 * non-local handlers (e.g., for logging or real-time telemetry).
 *
 * <p><strong>Example usage:</strong>
 * <pre>{@code
 * FluxCapacitor.publishMetrics(new SystemMetrics(...));
 * }</pre>
 *
 * @see Message
 * @see Guarantee
 * @see HasLocalHandlers
 */
public interface MetricsGateway extends HasLocalHandlers {

    /**
     * Publishes a metrics message using default metadata and {@link Guarantee#NONE}.
     * <p>
     * If the provided value is already a {@link Message}, its payload and metadata will be extracted. Otherwise, it is
     * wrapped in a new {@link Message}.
     *
     * @param metrics the metrics object or {@link Message} to publish
     */
    default void publish(Object metrics) {
        if (metrics instanceof Message) {
            publish(((Message) metrics).getPayload(), ((Message) metrics).getMetadata());
        } else {
            publish(metrics, Metadata.empty());
        }
    }

    /**
     * Publishes a metrics payload with the specified metadata and a {@link Guarantee#NONE} delivery guarantee.
     *
     * @param payload  the payload of the metrics message
     * @param metadata metadata to attach to the message
     */
    @SneakyThrows
    default void publish(Object payload, Metadata metadata) {
        publish(payload, metadata, Guarantee.NONE);
    }

    /**
     * Publishes a metrics message with the specified metadata and delivery guarantee.
     * <p>
     * This method may complete asynchronously.
     *
     * @param payload   the metrics payload
     * @param metadata  metadata to attach to the message
     * @param guarantee delivery guarantee (e.g., {@link Guarantee#SENT} or {@link Guarantee#STORED})
     * @return a {@link CompletableFuture} that completes when the message is published (or not, depending on the
     * guarantee)
     */
    CompletableFuture<Void> publish(Object payload, Metadata metadata, Guarantee guarantee);
}
