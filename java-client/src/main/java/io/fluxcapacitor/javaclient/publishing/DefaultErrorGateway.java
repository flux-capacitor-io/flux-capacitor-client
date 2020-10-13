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
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@AllArgsConstructor
@Slf4j
public class DefaultErrorGateway implements ErrorGateway {

    private final GatewayClient errorGateway;
    private final MessageSerializer serializer;

    @Delegate
    private final HandlerRegistry localHandlerRegistry;

    @Override
    public void report(Object payload, Metadata metadata, String target) {
        try {
            SerializedMessage message = serializer.serialize(new Message(payload, metadata));
            message.setTarget(target);
            Optional<CompletableFuture<Message>> result = localHandlerRegistry.handle(payload, message);
            if (result.isPresent() && result.get().isCompletedExceptionally()) {
                try {
                    result.get().getNow(null);
                } catch (CompletionException e) {
                    log.error("Failed to handle error locally", e);
                }
            }
            errorGateway.send(message);
        } catch (Exception e) {
            log.error("Failed to report error {}", payload, e);
        }
    }
}
