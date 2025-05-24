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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Position;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.With;

import java.util.Optional;

import static io.fluxcapacitor.common.ConsistentHashing.fallsInRange;


/**
 * Represents the client-side tracking context during message consumption in Flux Capacitor.
 * <p>
 * This {@code Tracker} is used locally in a Flux client to provide filtering and stateful context when consuming
 * message batches. It is automatically set as a thread-local variable for the duration of batch processing, allowing
 * handlers and other logic to access it via {@link #current()}.
 *
 * @see #current()
 * @see MessageBatch
 * @see Position
 */
@Value
public class Tracker {
    /**
     * Thread-local variable that holds the current tracker instance. This is automatically set by the Flux client.
     */
    public static final ThreadLocal<Tracker> current = new ThreadLocal<>();

    /**
     * Returns the current tracker instance, if any.
     */
    public static Optional<Tracker> current() {
        return Optional.ofNullable(current.get());
    }

    /**
     * An identifier for the current tracker instance.
     */
    String trackerId;

    /**
     * The type of messages being consumed (e.g., {@code EVENT}, {@code COMMAND}).
     */
    MessageType messageType;

    /**
     * The topic being tracked if {@code messageType} is {@link MessageType#CUSTOM} or {@link MessageType#DOCUMENT}.
     * Otherwise, {@code null}.
     */
    String topic;

    /**
     * Configuration settings such as the consumer name.
     */
    ConsumerConfiguration configuration;

    /**
     * The current batch being processed, including segment and position metadata.
     */
    @With
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    MessageBatch messageBatch;

    /**
     * Returns the name of the consumer associated with this tracker.
     */
    public String getName() {
        return configuration.getName();
    }

    /**
     * When the consumer is configured with {@code ignoreSegment=true}, segment filtering is performed client-side. This
     * method uses the current {@link MessageBatch} to determine whether a given message should be processed by the
     * current tracker, based on:
     * <ul>
     *     <li>The segment range of the batch</li>
     *     <li>The index of the message relative to the current {@link Position}</li>
     * </ul>
     *
     * @param message The message to check
     * @param routingKey The routing key to base the check on
     */
    public boolean canHandle(DeserializingMessage message, String routingKey) {
        if (messageBatch == null || messageBatch.getPosition() == null) {
            return true;
        }
        int segment = ConsistentHashing.computeSegment(routingKey);
        return fallsInRange(segment, messageBatch.getSegment())
               && messageBatch.getPosition().isNewIndex(segment, message.getIndex());
    }
}
