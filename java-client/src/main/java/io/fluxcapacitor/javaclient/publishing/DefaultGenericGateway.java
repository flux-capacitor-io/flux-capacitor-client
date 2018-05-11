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

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.AllArgsConstructor;

import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;

@AllArgsConstructor
public class DefaultGenericGateway implements GenericGateway {
    private final GatewayClient gatewayClient;
    private final RequestHandler requestHandler;
    private final MessageSerializer serializer;

    @Override
    public void sendAndForget(Object payload, Metadata metadata) {
        try {
            gatewayClient.send(serializer.serialize(payload, metadata));
        } catch (Exception e) {
            throw new GatewayException(format("Failed to send and forget %s", payload), e);
        }
    }

    @Override
    public CompletableFuture<Message> sendForMessage(Object payload, Metadata metadata) {
        try {
            return requestHandler.sendRequest(serializer.serialize(payload, metadata), gatewayClient::send);
        } catch (Exception e) {
            throw new GatewayException(format("Failed to send %s", payload), e);
        }
    }
}
