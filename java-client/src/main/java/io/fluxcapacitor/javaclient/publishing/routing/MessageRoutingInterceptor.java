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

@AllArgsConstructor
@Slf4j
public class MessageRoutingInterceptor implements DispatchInterceptor {
    @Override
    public Message interceptDispatch(Message message, MessageType messageType) {
        return message;
    }

    @Override
    public SerializedMessage modifySerializedMessage(SerializedMessage serializedMessage, Message m,
                                                     MessageType messageType) {
        if (serializedMessage.getSegment() == null) {
            m.computeRoutingKey().map(ConsistentHashing::computeSegment).ifPresent(serializedMessage::setSegment);
        }
        return serializedMessage;
    }
}
