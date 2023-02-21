/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import io.fluxcapacitor.javaclient.tracking.handling.Request;
import lombok.SneakyThrows;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.Arrays.stream;

public interface GenericGateway extends HasLocalHandlers {

    @SneakyThrows
    default void sendAndForget(Object message) {
        sendAndForget(asMessage(message), Guarantee.NONE).get();
    }

    @SneakyThrows
    default void sendAndForget(Object payload, Metadata metadata) {
        sendAndForget(new Message(payload, metadata), Guarantee.NONE).get();
    }

    @SneakyThrows
    default void sendAndForget(Object payload, Metadata metadata, Guarantee guarantee) {
        sendAndForget(new Message(payload, metadata), guarantee).get();
    }

    default CompletableFuture<Void> sendAndForget(Message message, Guarantee guarantee) {
        return sendAndForget(guarantee, message);
    }

    default void sendAndForget(Object... messages) {
        sendAndForget(Guarantee.NONE, messages);
    }

    default CompletableFuture<Void> sendAndForget(Guarantee guarantee, Object... messages) {
        return sendAndForget(guarantee, stream(messages).map(Message::asMessage).toArray(Message[]::new));
    }

    CompletableFuture<Void> sendAndForget(Guarantee guarantee, Message... messages);

    default <R> CompletableFuture<R> send(Message message) {
        return sendForMessage(message).thenApply(Message::getPayload);
    }

    default <R> CompletableFuture<R> send(Object message) {
        return send(asMessage(message));
    }

    default <R> CompletableFuture<R> send(Request<R> message) {
        return send((Object) message);
    }

    default <R> CompletableFuture<R> send(Object payload, Metadata metadata) {
        return send(new Message(payload, metadata));
    }

    default <R> CompletableFuture<R> send(Request<R> payload, Metadata metadata) {
        return send((Object) payload, metadata);
    }

    default CompletableFuture<Message> sendForMessage(Message message) {
        return sendForMessages(message).get(0);
    }

    default <R> List<CompletableFuture<R>> send(Object... messages) {
        return sendForMessages(Arrays.stream(messages).map(Message::asMessage).toArray(Message[]::new)).stream()
                .<CompletableFuture<R>>map(f -> f.thenApply(Message::getPayload)).collect(Collectors.toList());
    }

    List<CompletableFuture<Message>> sendForMessages(Message... messages);

    default <R> R sendAndWait(Object message) {
        return sendAndWait(asMessage(message));
    }

    default <R> R sendAndWait(Request<R> message) {
        return sendAndWait((Object) message);
    }

    @SneakyThrows
    default <R> R sendAndWait(Object payload, Metadata metadata) {
        return sendAndWait(new Message(payload, metadata));
    }

    default <R> R sendAndWait(Request<R> payload, Metadata metadata) {
        return sendAndWait((Object) payload, metadata);
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
            throw new TimeoutException(format("Request %s (type %s) has timed out", message.getMessageId(),
                                              message.getPayloadClass()));
        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new GatewayException(
                    format("Thread interrupted while waiting for result of %s (type %s)",
                           message.getMessageId(), message.getPayloadClass()), e);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    void close();
}
