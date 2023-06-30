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

@AllArgsConstructor
public class WebsocketResponseInterceptor implements DispatchInterceptor {
    @Override
    public Message interceptDispatch(Message message, MessageType messageType) {
        DeserializingMessage currentMessage = DeserializingMessage.getCurrent();
        if (currentMessage != null && currentMessage.getMessageType() == MessageType.WEBREQUEST) {
            HttpRequestMethod requestMethod = WebRequest.getMethod(currentMessage.getMetadata());
            if (requestMethod != null && requestMethod.isWebsocket()) {
                if (requestMethod == HttpRequestMethod.WS_HANDSHAKE) {
                    message = message.addMetadata(
                            "clientId", FluxCapacitor.getOptionally().map(fc -> fc.client().id()).orElse(null),
                            "trackerId", Tracker.current().map(Tracker::getTrackerId).orElse(null));
                } else {
                    return message.withMetadata(
                            message.getMetadata().with("sessionId", currentMessage.getMetadata().get("sessionId"))
                                                                  .addIfAbsent("function", "message"));
                }
            }
        }
        return message;
    }
}
