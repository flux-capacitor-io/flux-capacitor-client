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
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;

public interface RequestGateway {

    default void sendAndForget(Object payload) {
        sendAndForget(payload instanceof Message ? (Message) payload : new Message(payload, getMessageType()));
    }

    default void sendAndForget(Object payload, Metadata metadata) {
        sendAndForget(new Message(payload, metadata, getMessageType()));
    }

    void sendAndForget(Message message);

    default <R> CompletableFuture<R> send(Message message) {
        return sendForMessage(message).thenApply(Message::getPayload);
    }

    default <R> CompletableFuture<R> send(Object payload) {
        return send(payload instanceof Message ? (Message) payload : new Message(payload, getMessageType()));
    }

    default <R> CompletableFuture<R> send(Object payload, Metadata metadata) {
        return send(new Message(payload, metadata, getMessageType()));
    }

    CompletableFuture<Message> sendForMessage(Message message);
    
    default <R> R sendAndWait(Object payload) {
        return sendAndWait(payload instanceof Message ? (Message) payload : new Message(payload, getMessageType()));
    }

    @SneakyThrows
    default <R> R sendAndWait(Object payload, Metadata metadata) {
        return sendAndWait(new Message(payload, metadata, getMessageType()));
    }

    @SneakyThrows
    default <R> R sendAndWait(Message message) {
        CompletableFuture<R> future = send(message);
        try {
            Timeout timeout = message.getPayload().getClass().getAnnotation(Timeout.class);
            if (timeout != null) {
                return future.get(timeout.millis(), TimeUnit.MILLISECONDS);
            }
            return future.get(1, TimeUnit.MINUTES);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException(
                    format("%s has timed out", message.getPayload()), e);
        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new GatewayException(format("Thread interrupted while waiting for result of %s", message.getPayload()), e);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
    
    Registration registerLocalHandler(Object target);
    
    MessageType getMessageType();
    
}
