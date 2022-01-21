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
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;
import lombok.experimental.NonFinal;

import java.time.Instant;
import java.util.Map;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentClock;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;

@Value
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@NonFinal
public class Message {

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
        this(payload, Metadata.empty(), currentIdentityProvider().nextTechnicalId(), currentClock().instant());
    }

    public Message(Object payload, Metadata metadata) {
        this(payload, metadata, currentIdentityProvider().nextTechnicalId(), currentClock().instant());
    }

    @SuppressWarnings("unchecked")
    public <R> R getPayload() {
        return (R) payload;
    }

    public Message addMetaData(Metadata metadata) {
        return withMetadata(getMetadata().with(metadata));
    }

    public Message addMetaData(String key, Object value) {
        return withMetadata(getMetadata().with(key, value));
    }

    public Message addMetaData(Object... keyValues) {
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
