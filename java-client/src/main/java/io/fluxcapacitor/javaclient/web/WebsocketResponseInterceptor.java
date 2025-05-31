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

package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import lombok.AllArgsConstructor;

import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.isWebsocket;

/**
 * A {@link DispatchInterceptor} that modifies outgoing {@link WebResponse} messages in response to WebSocket-based
 * {@link WebRequest}s.
 * <p>
 * This interceptor ensures that appropriate metadata is included in the corresponding {@link WebResponse}. It supports
 * both the WebSocket handshake phase and subsequent message exchanges over an established WebSocket session.
 *
 * <p>Specifically:
 * <ul>
 *     <li>If the incoming message is a {@code WS_HANDSHAKE}, it adds the {@code clientId}
 *     and {@code trackerId} to the response metadata to identify the connection.</li>
 *     <li>If the session is already open, it includes the {@code sessionId} and, if
 *     absent, sets a {@code function} field to either {@code message} or {@code ack},
 *     depending on whether the outgoing message has a payload.</li>
 * </ul>
 *
 * <p>This ensures that the Flux WebSocket infrastructure can properly route and
 * interpret messages sent back to the client.
 */
@AllArgsConstructor
public class WebsocketResponseInterceptor implements DispatchInterceptor {

    /**
     * Intercepts and conditionally modifies an outgoing message in the context of a WebSocket request.
     *
     * @param message     the original message to be dispatched
     * @param messageType the type of the message (e.g., WEBRESPONSE)
     * @param topic       the topic to which the message is being published
     * @return the possibly modified message
     */
    @Override
    public Message interceptDispatch(Message message, MessageType messageType, String topic) {
        DeserializingMessage currentMessage = DeserializingMessage.getCurrent();
        if (currentMessage != null && currentMessage.getMessageType() == MessageType.WEBREQUEST) {
            String requestMethod = WebRequest.getMethod(currentMessage.getMetadata());
            if (isWebsocket(requestMethod)) {
                if (HttpRequestMethod.WS_HANDSHAKE.equals(requestMethod)) {
                    message = message.addMetadata(
                            "clientId", FluxCapacitor.getOptionally().map(fc -> fc.client().id()).orElse(null),
                            "trackerId", Tracker.current().map(Tracker::getTrackerId).orElse(null));
                } else {
                    return message.withMetadata(
                            message.getMetadata().with("sessionId", currentMessage.getMetadata().get("sessionId"))
                                    .addIfAbsent("function", message.getPayload() == null ? "ack" : "message"));
                }
            }
        }
        return message;
    }
}
