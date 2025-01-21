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

package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.handling.Invocation;
import jakarta.annotation.Nullable;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@Getter
public enum DefaultCorrelationDataProvider implements CorrelationDataProvider {
    INSTANCE;

    private final String applicationIdKey = "$applicationId",
            clientIdKey = "$clientId", clientNameKey = "$clientName", consumerKey = "$consumer",
            trackerKey = "$tracker", correlationIdKey = "$correlationId", traceIdKey = "$traceId",
            triggerKey = "$trigger", triggerTypeKey = "$triggerType", invocationKey = "$invocation";

    @Override
    public Map<String, String> getCorrelationData(@Nullable DeserializingMessage currentMessage) {
        Map<String, String> result = getBasicCorrelationData(
                FluxCapacitor.getOptionally().map(FluxCapacitor::client).orElse(null));
        ofNullable(currentMessage).ifPresent(m -> {
            String correlationId = ofNullable(m.getIndex()).map(Object::toString).orElse(m.getMessageId());
            result.put(this.correlationIdKey, correlationId);
            result.put(traceIdKey, currentMessage.getMetadata().getOrDefault(traceIdKey, correlationId));
            result.put(triggerKey, m.getType());
            result.put(triggerTypeKey, m.getMessageType().name());
            result.putAll(currentMessage.getMetadata().getTraceEntries());
        });
        return result;
    }

    @Override
    public Map<String, String> getCorrelationData(@Nullable Client client, @Nullable SerializedMessage currentMessage,
                                                  @Nullable MessageType messageType) {
        Map<String, String> result = getBasicCorrelationData(client);
        ofNullable(currentMessage).ifPresent(m -> {
            String correlationId = ofNullable(m.getIndex()).map(Object::toString).orElse(m.getMessageId());
            result.put(this.correlationIdKey, correlationId);
            result.put(traceIdKey, currentMessage.getMetadata().getOrDefault(traceIdKey, correlationId));
            result.put(triggerKey, m.getType());
            if (messageType != null) {
                result.put(triggerTypeKey, messageType.name());
            }
            result.putAll(currentMessage.getMetadata().getTraceEntries());
        });
        return result;
    }

    private HashMap<String, String> getBasicCorrelationData(@Nullable Client client) {
        var result = new HashMap<String, String>();
        Optional.ofNullable(client).ifPresent(f -> {
            Optional.ofNullable(client.applicationId())
                    .ifPresent(applicationId -> result.put(applicationIdKey, applicationId));
            result.put(clientIdKey, client.id());
            result.put(clientNameKey, client.name());
        });
        Tracker.current().ifPresent(t -> {
            result.put(consumerKey, t.getName());
            result.put(trackerKey, t.getTrackerId());
        });
        Optional.ofNullable(Invocation.getCurrent()).ifPresent(i -> result.put(invocationKey, i.getId()));
        return result;
    }
}
