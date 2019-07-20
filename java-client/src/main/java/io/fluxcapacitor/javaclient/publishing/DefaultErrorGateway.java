/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class DefaultErrorGateway implements ErrorGateway {

    private final GatewayClient errorGateway;
    private final MessageSerializer serializer;

    @Override
    public void report(Exception payload, Metadata metadata, String target) {
        try {
            SerializedMessage message = serializer.serialize(new Message(payload, metadata, MessageType.ERROR));
            message.setTarget(target);
            errorGateway.send(message);
        } catch (Exception e) {
            log.error("Failed to report error {}", payload, e);
        }
    }
}
