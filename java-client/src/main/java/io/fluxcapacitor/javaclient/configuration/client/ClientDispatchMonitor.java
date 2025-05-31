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

package io.fluxcapacitor.javaclient.configuration.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;

import java.util.List;

/**
 * Functional interface for monitoring the dispatch of messages by a {@link Client}.
 * <p>
 * Implementations of this interface can be registered via
 * {@link Client#monitorDispatch(ClientDispatchMonitor, MessageType...)} to observe when a batch of messages is
 * dispatched to a specific message gateway. This is useful for debugging, logging, metrics collection, or integration
 * testing.
 *
 * <p>
 * Note that monitors are invoked synchronously during dispatch and should return quickly to avoid delaying message
 * handling.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * client.monitorDispatch((type, topic, messages) -> {
 *     System.out.printf("Dispatched %d %s message(s) to topic %s%n",
 *         messages.size(), type, topic);
 * }, MessageType.COMMAND, MessageType.EVENT);
 * }</pre>
 *
 * @see Client#monitorDispatch(ClientDispatchMonitor, MessageType...)
 * @see MessageType
 * @see SerializedMessage
 */
@FunctionalInterface
public interface ClientDispatchMonitor {
    /**
     * Called when a batch of messages is dispatched by the client.
     *
     * @param messageType the type of the dispatched messages
     * @param topic       the topic they were dispatched to, or {@code null} if not applicable
     * @param messages    the messages that were dispatched
     */
    void accept(MessageType messageType, String topic, List<SerializedMessage> messages);
}
