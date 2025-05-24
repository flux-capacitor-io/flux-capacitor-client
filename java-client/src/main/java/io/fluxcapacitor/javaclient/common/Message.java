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

package io.fluxcapacitor.javaclient.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.Getter;
import lombok.Value;
import lombok.With;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;

/**
 * Represents a unit of communication within the Flux platform, wrapping a payload and its metadata.
 * <p>
 * A {@code Message} serves as the base class for all messages in Flux (e.g., commands, events, queries, web requests).
 * It contains a strongly typed payload, immutable metadata, a globally unique message ID, and a timestamp.
 * </p>
 *
 * <p>
 * Messages can be enriched with metadata, transformed with a new payload, or serialized for transmission.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Immutable, with {@code withX} and {@code addMetadata} methods for safe transformation</li>
 *   <li>Supports auto-generation of message ID and timestamp if not provided</li>
 *   <li>Lazy routing key computation via {@code getRoutingKey()}</li>
 * </ul>
 *
 * @see Metadata
 * @see Schedule
 * @see WebRequest
 * @see WebResponse
 */
@Value
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, defaultImpl = Message.class)
@NonFinal
@Slf4j
public class Message implements HasMessage {

    /**
     * Converts any object into a {@code Message}. If the object already implements {@link HasMessage}, the existing
     * message is returned.
     *
     * @param object the input object to wrap
     * @return a {@code Message} instance
     */
    public static Message asMessage(Object object) {
        return object instanceof HasMessage hm ? hm.toMessage() : new Message(object);
    }

    /**
     * The actual message payload, such as a command, event, or query object.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    Object payload;

    /**
     * Immutable metadata attached to the message. May include routing information, context, user info, etc.
     */
    @With
    Metadata metadata;

    /**
     * Unique technical identifier for the message. Auto-generated if not provided.
     */
    @With
    String messageId;

    /**
     * The creation timestamp of the message, in UTC. Defaults to the current time, truncated to milliseconds.
     */
    @With
    Instant timestamp;

    /**
     * Lazily computed routing key based on the payload and metadata. Used for partitioning or load balancing.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @JsonIgnore
    @Getter(lazy = true)
    Optional<String> routingKey = computeRoutingKey();

    /**
     * Constructs a message with the given payload and empty metadata.
     *
     * @param payload the message payload
     */
    public Message(Object payload) {
        this(payload, Metadata.empty());
    }

    /**
     * Constructs a message with the given payload and metadata.
     *
     * @param payload  the message payload
     * @param metadata the associated metadata
     */
    public Message(Object payload, Metadata metadata) {
        this(payload, metadata, null, null);
    }

    /**
     * Full constructor for internal use and deserialization.
     *
     * @param payload   the payload
     * @param metadata  metadata for the message
     * @param messageId optional unique ID; auto-generated if {@code null}
     * @param timestamp optional creation time; defaults to current time if {@code null}
     */
    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp"})
    public Message(Object payload, Metadata metadata, String messageId, Instant timestamp) {
        this.payload = payload;
        this.metadata = Optional.ofNullable(metadata).orElseGet(Metadata::empty);
        this.messageId = Optional.ofNullable(messageId).orElseGet(() -> currentIdentityProvider().nextTechnicalId());
        this.timestamp = Optional.ofNullable(timestamp)
                .orElseGet(() -> FluxCapacitor.currentTime().truncatedTo(ChronoUnit.MILLIS));
    }

    /**
     * Returns the strongly typed payload of the message.
     *
     * @param <R> the expected type of the payload
     * @return the payload
     */
    @SuppressWarnings("unchecked")
    public <R> R getPayload() {
        return (R) payload;
    }

    @Override
    public Message toMessage() {
        return this;
    }

    /**
     * Returns a new message instance with the provided payload and existing metadata, ID, and timestamp.
     */
    public Message withPayload(Object payload) {
        if (payload == getPayload()) {
            return this;
        }
        return new Message(payload, metadata, messageId, timestamp);
    }

    /**
     * Returns a new message with the combined metadata.
     */
    public Message addMetadata(Metadata metadata) {
        return withMetadata(getMetadata().with(metadata));
    }

    /**
     * Adds a single metadata entry.
     */
    public Message addMetadata(String key, Object value) {
        return withMetadata(getMetadata().with(key, value));
    }

    /**
     * Adds multiple metadata entries.
     */
    public Message addMetadata(Object... keyValues) {
        return withMetadata(getMetadata().with(keyValues));
    }

    /**
     * Adds metadata from a given map.
     */
    public Message addMetadata(Map<String, ?> values) {
        return withMetadata(getMetadata().with(values));
    }

    /**
     * Attaches a user object to the metadata using the configured {@link UserProvider}.
     */
    public Message addUser(User user) {
        return addMetadata(FluxCapacitor.getOptionally().map(FluxCapacitor::userProvider)
                                   .or(() -> Optional.ofNullable(UserProvider.defaultUserProvider))
                                   .orElseThrow(() -> new IllegalStateException("User provider is not set"))
                                   .addToMetadata(getMetadata(), user));
    }

    /**
     * Serializes this message to a {@link SerializedMessage} using the provided {@link Serializer}.
     */
    public SerializedMessage serialize(Serializer serializer) {
        return new SerializedMessage(serializer.serialize(payload), metadata, messageId, timestamp.toEpochMilli());
    }
}
