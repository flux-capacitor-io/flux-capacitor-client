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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentTime;
import static java.util.Optional.ofNullable;

/**
 * Strategy interface for extracting correlation metadata from the current context.
 * <p>
 * Correlation metadata is used to propagate contextual information (e.g. trace IDs, user info, client IDs) across
 * messages within the same logical execution chain.
 * <p>
 * When a message is dispatched, the active {@code CorrelationDataProvider} is queried to generate key-value pairs that
 * are added to the message's metadata. These entries can then be accessed by downstream handlers to maintain
 * traceability and context awareness.
 *
 * <p>
 * A {@code CorrelationDataProvider} may extract metadata based on:
 * <ul>
 *     <li>The {@link DeserializingMessage} currently being handled</li>
 *     <li>The {@link SerializedMessage} most recently published</li>
 *     <li>The {@link Client} that is processing the message</li>
 *     <li>The {@link MessageType} of the outgoing message</li>
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
 * <p>
 * Multiple providers can be composed using {@link #andThen(CorrelationDataProvider)} to combine metadata from multiple sources.
 *
 * @see io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor
 * @see io.fluxcapacitor.javaclient.publishing.correlation.DefaultCorrelationDataProvider
 */
public interface CorrelationDataProvider {

    /**
     * Returns correlation metadata based on the current message being handled (if any).
     * <p>
     * Delegates to {@link #getCorrelationData(DeserializingMessage)} using the current thread-local
     * {@link DeserializingMessage}.
     *
     * @return a map of correlation metadata entries
     */
    default Map<String, String> getCorrelationData() {
        return getCorrelationData(DeserializingMessage.getCurrent());
    }

    /**
     * Returns correlation metadata derived from the current deserializing message.
     *
     * @param currentMessage the message currently being handled (can be {@code null})
     * @return a map of correlation metadata entries
     */
    default Map<String, String> getCorrelationData(@Nullable DeserializingMessage currentMessage) {
        Map<String, String> result = getBasicCorrelationData(
                FluxCapacitor.getOptionally().map(FluxCapacitor::client).orElse(null));
        ofNullable(currentMessage).ifPresent(m -> {
            String correlationId = ofNullable(m.getIndex()).map(Object::toString).orElse(m.getMessageId());
            result.put(getCorrelationIdKey(), correlationId);
            result.put(getTraceIdKey(), currentMessage.getMetadata().getOrDefault(getTraceIdKey(), correlationId));
            result.put(getTriggerKey(), m.getType());
            result.put(getTriggerTypeKey(), m.getMessageType().name());
            result.putAll(currentMessage.getMetadata().getTraceEntries());
            result.put(getDelayKey(), Long.toString(Duration.between(m.getTimestamp(), currentTime()).toMillis()));
        });
        return result;
    }

    /**
     * Returns correlation metadata derived from a serialized message and optional context.
     * <p>
     * This method may be invoked when publishing messages without a {@link DeserializingMessage} context, such as in
     * asynchronous dispatch or system-level operations.
     *
     * @param client         the client instance performing the dispatch (can be {@code null})
     * @param currentMessage the last serialized message in context (can be {@code null})
     * @param messageType    the type of the outgoing message (e.g. {@code COMMAND}, {@code EVENT}, etc.)
     * @return a map of correlation metadata entries
     */
    default Map<String, String> getCorrelationData(@Nullable Client client,
                                                   @Nullable SerializedMessage currentMessage,
                                                   @Nullable MessageType messageType) {
        Map<String, String> result = getBasicCorrelationData(client);
        ofNullable(currentMessage).ifPresent(m -> {
            String correlationId = ofNullable(m.getIndex()).map(Object::toString).orElse(m.getMessageId());
            result.put(getCorrelationIdKey(), correlationId);
            result.put(getTraceIdKey(), currentMessage.getMetadata().getOrDefault(getTraceIdKey(), correlationId));
            result.put(getTriggerKey(), m.getType());
            if (messageType != null) {
                result.put(getTriggerTypeKey(), messageType.name());
            }
            result.putAll(currentMessage.getMetadata().getTraceEntries());
        });
        return result;
    }

    private HashMap<String, String> getBasicCorrelationData(@Nullable Client client) {
        var result = new HashMap<String, String>();
        Optional.ofNullable(client).ifPresent(f -> {
            Optional.ofNullable(client.applicationId())
                    .ifPresent(applicationId -> result.put(getApplicationIdKey(), applicationId));
            result.put(getClientIdKey(), client.id());
            result.put(getClientNameKey(), client.name());
        });
        Tracker.current().ifPresent(t -> {
            result.put(getConsumerKey(), t.getName());
            result.put(getTrackerKey(), t.getTrackerId());
        });
        Optional.ofNullable(Invocation.getCurrent()).ifPresent(i -> result.put(getInvocationKey(), i.getId()));
        return result;
    }


    /**
     * Retrieves the key used to identify the application ID in correlation metadata.
     *
     * @return a string representing the application ID key
     */
    default String getApplicationIdKey() {
        return "$applicationId";
    }

    /**
     * Retrieves the key representing the client identifier in the correlation metadata.
     *
     * @return the key used to identify the client in correlation data
     */
    default String getClientIdKey() {
        return "$clientId";
    }

    /**
     * Retrieves the key associated with the client's name from the correlation metadata.
     *
     * @return a string representing the key for the client's name
     */
    default String getClientNameKey() {
        return "$clientName";
    }

    /**
     * Retrieves the consumer key used to identify the consumer of a message.
     *
     * @return a string representing the consumer key
     */
    default String getConsumerKey() {
        return "$consumer";
    }

    /**
     * Retrieves the key representing the tracker component in correlation metadata.
     *
     * @return the tracker key as a string
     */
    default String getTrackerKey() {
        return "$tracker";
    }

    /**
     * Returns the key used for identifying the correlation ID within the metadata.
     *
     * @return a string representing the key for the correlation ID
     */
    default String getCorrelationIdKey() {
        return "$correlationId";
    }

    /**
     * Returns the key used to identify the trace ID in correlation metadata.
     *
     * @return a string representing the trace ID key.
     */
    default String getTraceIdKey() {
        return "$traceId";
    }

    /**
     * Retrieves the trigger key used to identify the triggering context for correlation metadata.
     *
     * @return the trigger key as a string
     */
    default String getTriggerKey() {
        return "$trigger";
    }

    /**
     * Returns the key associated with the trigger type in the correlation data.
     *
     * @return a string representing the trigger type key
     */
    default String getTriggerTypeKey() {
        return "$triggerType";
    }

    /**
     * Retrieves the key associated with the invocation context within the correlation metadata.
     *
     * @return the invocation key as a string
     */
    default String getInvocationKey() {
        return "$invocation";
    }

    /**
     * Retrieves the key used to identify the delay metadata within correlation data.
     *
     * @return a string representing the delay metadata key
     */
    default String getDelayKey() {
        return "$msDelay";
    }

    /**
     * Chains this provider with another, returning a composed provider that merges the metadata from both.
     * <p>
     * If keys overlap, the values from the second (next) provider will overwrite those from this provider.
     *
     * @param next the provider to apply after this one
     * @return a composed {@code CorrelationDataProvider} that merges the results of both
     */
    default CorrelationDataProvider andThen(CorrelationDataProvider next) {
        CorrelationDataProvider first = this;

        return new CorrelationDataProvider() {
            @Override
            public Map<String, String> getCorrelationData(@Nullable DeserializingMessage currentMessage) {
                Map<String, String> result = new HashMap<>(first.getCorrelationData(currentMessage));
                result.putAll(next.getCorrelationData(currentMessage));
                return result;
            }

            @Override
            public Map<String, String> getCorrelationData(Client client, @Nullable SerializedMessage currentMessage,
                                                          @Nullable MessageType messageType) {
                Map<String, String> result =
                        new HashMap<>(first.getCorrelationData(client, currentMessage, messageType));
                result.putAll(next.getCorrelationData(client, currentMessage, messageType));
                return result;
            }
        };
    }
}
