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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;

import static java.util.Optional.ofNullable;

/**
 * Mechanism that enables modification, monitoring, or conditional suppression of messages before they are dispatched to
 * local handlers or published to the Flux platform.
 * <p>
 * A {@code DispatchInterceptor} allows observing and transforming messages during the dispatch process. It is typically
 * used to inject metadata, rewrite payloads, log outgoing messages, or prevent dispatching certain messages based on
 * custom rules.
 *
 * <p><strong>Key behaviors:</strong>
 * <ul>
 *   <li>{@link #interceptDispatch} is used for altering or blocking a message before it's handled or published.</li>
 *   <li>{@link #modifySerializedMessage} can change the final serialized message before it's stored or sent to the Flux platform.</li>
 *   <li>{@link #monitorDispatch} is a post-processing hook for monitoring without side effects or blocking.</li>
 * </ul>
 *
 * @see io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder#addDispatchInterceptor
 */
@FunctionalInterface
public interface DispatchInterceptor {

    /**
     * No-op implementation of the {@code DispatchInterceptor} that returns the original message unchanged.
     */
    DispatchInterceptor noOp = (m, messageType, topic) -> m;

    /**
     * Intercepts the dispatch of a message before it is serialized and published or locally handled.
     * <p>
     * You may modify the message or return {@code null} to block dispatching. Throwing an exception also prevents
     * dispatching.
     *
     * @param message     the message to be dispatched
     * @param messageType the type of the message (e.g., COMMAND, EVENT, etc.)
     * @param topic       the target topic or null if not applicable
     * @return the modified message, the same message, or {@code null} to prevent dispatch
     */
    Message interceptDispatch(Message message, MessageType messageType, String topic);

    /**
     * Allows modifications to the serialized representation of the message before it is actually published.
     * <p>
     * This is called after {@link #interceptDispatch} and should not be used to block dispatching — use
     * {@link #interceptDispatch} for that purpose instead.
     *
     * @param serializedMessage the serialized form of the message
     * @param message           the deserialized message object
     * @param messageType       the message type
     * @param topic             the target topic
     * @return the modified or original {@link SerializedMessage}
     */
    default SerializedMessage modifySerializedMessage(SerializedMessage serializedMessage,
                                                      Message message, MessageType messageType, String topic) {
        return serializedMessage;
    }

    /**
     * Hook to observe the dispatch of a message. This method is called after all interceptors have had a chance to
     * block or modify the message.
     * <p>
     * Use this for logging or metrics, but <em>not</em> to alter or block the message.
     *
     * @param message     the final message about to be handled or published
     * @param messageType the type of the message
     * @param topic       the topic to which the message is dispatched (can be null)
     */
    default void monitorDispatch(Message message, MessageType messageType, String topic) {
        // No-op by default
    }

    /**
     * Chains this interceptor with another. The resulting interceptor applies this one first, then the next one.
     *
     * @param nextInterceptor the interceptor to run after this one
     * @return a new {@code DispatchInterceptor} representing the combined logic
     */
    default DispatchInterceptor andThen(DispatchInterceptor nextInterceptor) {
        return new DispatchInterceptor() {
            @Override
            public Message interceptDispatch(Message m, MessageType t, String topic) {
                return ofNullable(DispatchInterceptor.this.interceptDispatch(m, t, topic))
                        .map(message -> nextInterceptor.interceptDispatch(message, t, topic)).orElse(null);
            }

            @Override
            public void monitorDispatch(Message message, MessageType messageType, String topic) {
                DispatchInterceptor.this.monitorDispatch(message, messageType, topic);
                nextInterceptor.monitorDispatch(message, messageType, topic);
            }

            @Override
            public SerializedMessage modifySerializedMessage(SerializedMessage s, Message m,
                                                             MessageType type, String topic) {
                return ofNullable(DispatchInterceptor.this.modifySerializedMessage(s, m, type, topic))
                        .map(message -> nextInterceptor.modifySerializedMessage(message, m, type, topic)).orElse(null);
            }
        };
    }
}
