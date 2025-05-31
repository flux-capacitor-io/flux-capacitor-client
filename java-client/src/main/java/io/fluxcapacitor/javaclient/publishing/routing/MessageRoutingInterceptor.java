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

package io.fluxcapacitor.javaclient.publishing.routing;

import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@link DispatchInterceptor} that assigns a routing segment to messages prior to dispatch.
 *
 * <p>This interceptor computes a consistent hash-based segment index for each message using
 * {@link ConsistentHashing#computeSegment(String)} and injects it into the serialized message if no segment has already
 * been set.
 *
 * <p>Flux Platform uses the segment value for consistent message routing and load distribution.
 * It ensures that all messages with the same routing key are handled by the same segment, preserving message affinity
 * and ordering guarantees within that segment.
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>If {@code SerializedMessage#getSegment()} is {@code null}, the interceptor computes the segment
 *       from the message's routing key via {@link Message#computeRoutingKey()}.</li>
 *   <li>If a segment is already assigned, the interceptor leaves it unchanged.</li>
 *   <li>The message content itself is not modified; only the serialized representation is updated.</li>
 * </ul>
 *
 * <p>This interceptor is typically enabled by default for most gateway clients (e.g., command, event, query, etc.),
 * ensuring that routing behavior is applied uniformly before messages are published to Flux Platform.
 *
 * @see DispatchInterceptor
 * @see ConsistentHashing
 * @see SerializedMessage#setSegment(Integer)
 */
@AllArgsConstructor
@Slf4j
public class MessageRoutingInterceptor implements DispatchInterceptor {
    /**
     * Returns the unmodified {@link Message} as this interceptor only modifies the serialized form.
     */
    @Override
    public Message interceptDispatch(Message message, MessageType messageType, String topic) {
        return message;
    }

    /**
     * Computes and sets the routing segment on the serialized message if not already present.
     *
     * @param serializedMessage the message to be sent
     * @param m                 the original message object
     * @param messageType       the type of message (e.g., command, event, query)
     * @param topic             the topic to which the message will be published
     * @return the same {@code SerializedMessage} instance, possibly updated with a segment
     */
    @Override
    public SerializedMessage modifySerializedMessage(SerializedMessage serializedMessage, Message m,
                                                     MessageType messageType, String topic) {
        if (serializedMessage.getSegment() == null) {
            m.computeRoutingKey().map(ConsistentHashing::computeSegment).ifPresent(serializedMessage::setSegment);
        }
        return serializedMessage;
    }
}
