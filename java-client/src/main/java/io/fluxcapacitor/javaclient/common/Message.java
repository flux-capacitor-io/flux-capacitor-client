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
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
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

@Value
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@NonFinal
@Slf4j
public class Message implements HasMessage {

    public static Message asMessage(Object object) {
        return object instanceof HasMessage ? ((HasMessage) object).toMessage() : new Message(object);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    Object payload;
    @With
    Metadata metadata;
    @With
    String messageId;
    @With
    Instant timestamp;

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @JsonIgnore
    @Getter(lazy = true)
    Optional<String> routingKey = computeRoutingKey();

    public Message(Object payload) {
        this(payload, Metadata.empty());
    }

    public Message(Object payload, Metadata metadata) {
        this(payload, metadata, null, null);
    }

    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp"})
    public Message(Object payload, Metadata metadata, String messageId, Instant timestamp) {
        this.payload = payload;
        this.metadata = Optional.ofNullable(metadata).orElseGet(Metadata::empty);
        this.messageId = Optional.ofNullable(messageId).orElseGet(() -> currentIdentityProvider().nextTechnicalId());
        this.timestamp = Optional.ofNullable(timestamp)
                .orElseGet(() -> FluxCapacitor.currentTime().truncatedTo(ChronoUnit.MILLIS));
    }

    @SuppressWarnings("unchecked")
    public <R> R getPayload() {
        return (R) payload;
    }

    @Override
    public Message toMessage() {
        return this;
    }

    public Message withPayload(Object payload) {
        if (payload == getPayload()) {
            return this;
        }
        return new Message(payload, metadata, messageId, timestamp);
    }

    public Message addMetadata(Metadata metadata) {
        return withMetadata(getMetadata().with(metadata));
    }

    public Message addMetadata(String key, Object value) {
        return withMetadata(getMetadata().with(key, value));
    }

    public Message addMetadata(Object... keyValues) {
        return withMetadata(getMetadata().with(keyValues));
    }

    public Message addMetadata(Map<String, ?> values) {
        return withMetadata(getMetadata().with(values));
    }

    public Message addUser(User user) {
        return addMetadata(FluxCapacitor.getOptionally().map(FluxCapacitor::userProvider)
                .or(() -> Optional.ofNullable(UserProvider.defaultUserSupplier))
                .orElseThrow(() -> new IllegalStateException("User provider is not set"))
                .addToMetadata(getMetadata(), user));
    }

    public SerializedMessage serialize(Serializer serializer) {
        return new SerializedMessage(serializer.serialize(payload), metadata, messageId, timestamp.toEpochMilli());
    }
}
