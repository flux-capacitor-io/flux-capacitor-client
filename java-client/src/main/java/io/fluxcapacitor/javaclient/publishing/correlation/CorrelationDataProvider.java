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
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy interface for extracting correlation metadata from the current context.
 * <p>
 * Correlation metadata is used to propagate contextual information (e.g. trace IDs, user info, client IDs)
 * across messages within the same logical execution chain.
 * <p>
 * When a message is dispatched, the active {@code CorrelationDataProvider} is queried to generate key-value pairs
 * that are added to the message's metadata. These entries can then be accessed by downstream handlers to maintain
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
    Map<String, String> getCorrelationData(@Nullable DeserializingMessage currentMessage);

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
    Map<String, String> getCorrelationData(@Nullable Client client,
                                           @Nullable SerializedMessage currentMessage,
                                           @Nullable MessageType messageType);

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
                Map<String, String> result = new HashMap<>(first.getCorrelationData(client, currentMessage, messageType));
                result.putAll(next.getCorrelationData(client, currentMessage, messageType));
                return result;
            }
        };
    }
}
