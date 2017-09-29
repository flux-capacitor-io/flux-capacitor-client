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
import io.fluxcapacitor.common.api.Message;
import io.fluxcapacitor.javaclient.tracking.ProducerService;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.GenericMessage;

import static java.util.Collections.singletonMap;

@Slf4j
public class ReplyingCallback<C, R> implements CommandCallback<C, R> {

    private final ProducerService resultProducerService;
    private final AxonMessageSerializer serializer;

    public ReplyingCallback(ProducerService resultProducerService, AxonMessageSerializer serializer) {
        this.resultProducerService = resultProducerService;
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
            resultProducerService.send(toMessage(result == null ? Void.TYPE : result, commandMessage)).await();
        } catch (Exception e) {
            log.error("Failed to send result {} of {}. Ignoring this and moving on.", result, commandMessage, e);
        }
    }

    protected Message toMessage(Object result, CommandMessage<? extends C> commandMessage) {
        Message message = new Message(serializer.serialize(
                new GenericMessage<>(result, singletonMap("correlationId", commandMessage.getIdentifier()))));
        message.setTarget((String) commandMessage.getMetaData().get("sender"));
        message.setType(result.getClass().getName());
        return message;
    }
}
