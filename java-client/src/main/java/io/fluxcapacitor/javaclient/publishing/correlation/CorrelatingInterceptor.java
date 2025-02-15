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
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import lombok.AllArgsConstructor;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentCorrelationData;

@AllArgsConstructor
public class CorrelatingInterceptor implements DispatchInterceptor {
    @Override
    public Message interceptDispatch(Message message, MessageType messageType, String topic) {
        Metadata metadata = message.getMetadata();
        if (messageType == MessageType.EVENT) {
            DeserializingMessage currentMessage = DeserializingMessage.getCurrent();
            if (currentMessage != null && currentMessage.getMessageType().isRequest()) {
                metadata = currentMessage.getMetadata().with(metadata);
            }
        }
        return message.withMetadata(metadata.with(currentCorrelationData()));
    }
}
