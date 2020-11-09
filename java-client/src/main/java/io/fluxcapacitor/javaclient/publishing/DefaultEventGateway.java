/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static java.lang.String.format;

@AllArgsConstructor
public class DefaultEventGateway implements EventGateway {
    private final GatewayClient gatewayClient;
    private final MessageSerializer serializer;
    @Delegate
    private final HandlerRegistry localHandlerRegistry;

    @Override
    @SneakyThrows
    public void publish(Message message) {
        SerializedMessage serializedMessage = serializer.serialize(message);
        Optional<CompletableFuture<Message>> result =
                localHandlerRegistry.handle(message.getPayload(), serializedMessage);
        if (result.isPresent() && result.get().isCompletedExceptionally()) {
            try {
                result.get().getNow(null);
            } catch (CompletionException e) {
                throw e.getCause();
            }
        }
        try {
            gatewayClient.send(serializedMessage);
        } catch (Exception e) {
            throw new GatewayException(format("Failed to send and forget %s", message.getPayload().toString()), e);
        }
    }
}
