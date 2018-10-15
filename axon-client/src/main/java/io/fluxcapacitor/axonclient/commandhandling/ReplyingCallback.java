/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.axonclient.commandhandling;

import io.fluxcapacitor.axonclient.common.serialization.AxonMessageSerializer;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.GenericMessage;

import static java.util.Collections.singletonMap;

@Slf4j
public class ReplyingCallback<C, R> implements CommandCallback<C, R> {

    private final GatewayClient resultGatewayClient;
    private final AxonMessageSerializer serializer;

    public ReplyingCallback(GatewayClient resultGatewayClient, AxonMessageSerializer serializer) {
        this.resultGatewayClient = resultGatewayClient;
        this.serializer = serializer;
    }

    @Override
    public void onSuccess(CommandMessage<? extends C> commandMessage, R result) {
        if (expectsResult(commandMessage)) {
            sendReply(commandMessage, result);
        }
    }

    @Override
    public void onFailure(CommandMessage<? extends C> commandMessage, Throwable cause) {
        if (expectsResult(commandMessage)) {
            sendReply(commandMessage, cause);
        } else {
            log.warn("Command '{}' resulted in {}({})", commandMessage.getCommandName(), cause.getClass().getName(),
                     cause.getMessage());
        }
    }

    protected boolean expectsResult(CommandMessage<?> commandMessage) {
        return commandMessage.getMetaData().containsKey("sender");
    }

    protected void sendReply(CommandMessage<? extends C> commandMessage, Object result) {
        try {
            resultGatewayClient.send(toMessage(result == null ? Void.TYPE : result, commandMessage)).await();
        } catch (Exception e) {
            log.error("Failed to send result {} of {}. Ignoring this and moving on.", result, commandMessage, e);
        }
    }

    protected SerializedMessage toMessage(Object result, CommandMessage<? extends C> commandMessage) {
        SerializedMessage message = new SerializedMessage(new Data<>(serializer.serialize(
                new GenericMessage<>(result, singletonMap("correlationId", commandMessage.getIdentifier()))),
                                                                     result.getClass().getName(), 0), Metadata.empty(),
                                                          commandMessage.getIdentifier());
        message.setTarget((String) commandMessage.getMetaData().get("sender"));
        return message;
    }
}
