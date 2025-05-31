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

/**
 * Default implementation of the {@link CorrelationDataProvider} interface.
 * <p>
 * This provider automatically assembles standard correlation metadata that is attached to outgoing messages
 * in a Flux Capacitor application. This correlation data enables tracing, auditing, monitoring, and debugging
 * across asynchronous message flows.
 *
 * <p>It gathers correlation context from multiple sources, including:
 * <ul>
 *   <li>The current {@link Client}</li>
 *   <li>The current {@link Tracker} if one is active</li>
 *   <li>The current {@link DeserializingMessage} being handled</li>
 *   <li>The current {@link Invocation} context</li>
 * </ul>
 *
 * <h2>Standard Metadata Keys</h2>
 * The correlation data map may include the following keys:
 * <ul>
 *   <li><b>{@code $applicationId}</b> – ID of the application this client belongs to (optional)</li>
 *   <li><b>{@code $clientId}</b> – Unique identifier for this Flux client instance</li>
 *   <li><b>{@code $clientName}</b> – Logical name of the client (e.g. "service-A")</li>
 *   <li><b>{@code $consumer}</b> – Consumer name of the current {@link Tracker}, if active</li>
 *   <li><b>{@code $tracker}</b> – Unique ID of the current {@link Tracker}, if active</li>
 *   <li><b>{@code $correlationId}</b> – ID used to correlate this message with the currently handled message (message index or ID if index is unavailable)</li>
 *   <li><b>{@code $traceId}</b> – ID representing the trace this message belongs to (usually inherited from the message root)</li>
 *   <li><b>{@code $trigger}</b> – Fully qualified class name of the message that triggered this one</li>
 *   <li><b>{@code $triggerType}</b> – Type of the triggering message (e.g. COMMAND, QUERY, etc.)</li>
 *   <li><b>{@code $invocation}</b> – ID of the current handler {@link Invocation}, if any</li>
 * </ul>
 *
 * <p>In addition to these fields, trace-level metadata from the current message
 * (e.g. custom entries marked as traceable) is also included.
 *
 * <p>This correlation metadata is typically added to outgoing messages automatically via
 * the {@link CorrelatingInterceptor}.
 *
 * @see CorrelationDataProvider
 * @see CorrelatingInterceptor
 * @see FluxCapacitor#currentCorrelationData()
 */
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
