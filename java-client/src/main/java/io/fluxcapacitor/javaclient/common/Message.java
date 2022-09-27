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

package io.fluxcapacitor.javaclient.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.Value;
import lombok.With;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentClock;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;

@Value
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@NonFinal
@Slf4j
public class Message implements HasMessage {

    public static Message asMessage(Object object) {
        return object instanceof Message ? (Message) object : new Message(object);
    }

    @With
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    Object payload;
    @With
    Metadata metadata;
    String messageId;
    Instant timestamp;

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
        this.timestamp = Optional.ofNullable(timestamp).orElseGet(() -> currentClock().instant());
    }

    @SuppressWarnings("unchecked")
    public <R> R getPayload() {
        return (R) payload;
    }

    public Class<?> getPayloadClass() {
        return payload == null ? Void.class : payload.getClass();
    }

    @Override
    public Message toMessage() {
        return this;
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

    public SerializedMessage serialize(Serializer serializer) {
        return new SerializedMessage(serializer.serialize(getPayload()), getMetadata(), getMessageId(),
                                     getTimestamp().toEpochMilli());
    }
}
