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

/**
 * A generic message gateway for publication of messages in Flux Capacitor.
 * <p>
 * The {@code GenericGateway} provides a uniform interface to send and receive messages over any standard or
 * user-defined topic, enabling extensibility beyond the default types such as commands, queries, events, and errors.
 * <p>
 * This interface supports asynchronous and synchronous message dispatching, both with and without awaiting a result. It
 * also includes support for delivery guarantees and local handler registration.
 * <p>
 * Typical usage includes:
 * <ul>
 *     <li>Custom message flows that don’t fit standard message types</li>
 *     <li>Internal APIs that benefit from topic-based routing</li>
 *     <li>Feature-specific messaging with isolated topics</li>
 * </ul>
 *
 * <h2>Obtaining a Gateway</h2>
 * Use {@link io.fluxcapacitor.javaclient.FluxCapacitor#customGateway(String)} to create or retrieve a
 * {@code GenericGateway} for a given topic:
 * <pre>{@code
 * GenericGateway myGateway = FluxCapacitor.customGateway("myTopic");
 * }</pre>
 *
 * @see HasLocalHandlers
 * @see io.fluxcapacitor.javaclient.publishing.CommandGateway
 * @see io.fluxcapacitor.javaclient.publishing.QueryGateway
 * @see io.fluxcapacitor.javaclient.publishing.EventGateway
 */
public interface GenericGateway extends HasLocalHandlers {

    /**
     * Sends a message asynchronously without waiting for a result or acknowledgement.
     */
    @SneakyThrows
    default void sendAndForget(Object message) {
        sendAndForget(asMessage(message), Guarantee.NONE).get();
    }

    /**
     * Sends a message with custom payload and metadata asynchronously.
     */
    @SneakyThrows
    default void sendAndForget(Object payload, Metadata metadata) {
        sendAndForget(new Message(payload, metadata), Guarantee.NONE).get();
    }

    /**
     * Sends a message with payload, metadata, and delivery guarantee asynchronously.
     */
    @SneakyThrows
    default void sendAndForget(Object payload, Metadata metadata, Guarantee guarantee) {
        sendAndForget(new Message(payload, metadata), guarantee).get();
    }

    /**
     * Sends a {@link Message} asynchronously with a given guarantee.
     */
    default CompletableFuture<Void> sendAndForget(Message message, Guarantee guarantee) {
        return sendAndForget(guarantee, message);
    }

    /**
     * Sends multiple messages asynchronously with {@link Guarantee#NONE}.
     */
    default void sendAndForget(Object... messages) {
        sendAndForget(Guarantee.NONE, messages);
    }

    /**
     * Sends multiple messages asynchronously with a specified delivery guarantee.
     */
    default CompletableFuture<Void> sendAndForget(Guarantee guarantee, Object... messages) {
        return sendAndForget(guarantee, stream(messages).map(Message::asMessage).toArray(Message[]::new));
    }

    /**
     * Sends multiple {@link Message} objects with a guarantee.
     */
    CompletableFuture<Void> sendAndForget(Guarantee guarantee, Message... messages);

    /**
     * Sends a {@link Message} and returns a future that completes with its response payload.
     */
    default <R> CompletableFuture<R> send(Message message) {
        return sendForMessage(message).thenApply(Message::getPayload);
    }

    /**
     * Sends a message (raw object or {@link Request}) and returns a future with its response.
     */
    default <R> CompletableFuture<R> send(Object message) {
        return send(asMessage(message));
    }

    /**
     * Sends a {@link Request} message and returns a future with its typed response.
     */
    default <R> CompletableFuture<R> send(Request<R> message) {
        return send((Object) message);
    }

    /**
     * Sends a message with custom metadata and returns a future with its response.
     */
    default <R> CompletableFuture<R> send(Object payload, Metadata metadata) {
        return send(new Message(payload, metadata));
    }

    /**
     * Sends a {@link Request} with metadata and returns a future with its response.
     */
    default <R> CompletableFuture<R> send(Request<R> payload, Metadata metadata) {
        return send((Object) payload, metadata);
    }

    /**
     * Sends a single {@link Message} and returns a future that resolves to the complete {@link Message} response.
     */
    default CompletableFuture<Message> sendForMessage(Message message) {
        return sendForMessages(message).get(0);
    }

    /**
     * Sends multiple messages and returns a list of futures with their payload responses.
     */
    default <R> List<CompletableFuture<R>> send(Object... messages) {
        return sendForMessages(Arrays.stream(messages).map(Message::asMessage).toArray(Message[]::new)).stream()
                .<CompletableFuture<R>>map(f -> f.thenApply(Message::getPayload)).collect(Collectors.toList());
    }

    /**
     * Sends multiple messages and returns futures for their full {@link Message} responses.
     */
    List<CompletableFuture<Message>> sendForMessages(Message... messages);

    /**
     * Sends a message and blocks until a response is received.
     */
    default <R> R sendAndWait(Object message) {
        return sendAndWait(asMessage(message));
    }

    /**
     * Sends a {@link Request} and blocks until a response is received.
     */
    default <R> R sendAndWait(Request<R> message) {
        return sendAndWait((Object) message);
    }

    /**
     * Sends a message with metadata and blocks for a response.
     */
    @SneakyThrows
    default <R> R sendAndWait(Object payload, Metadata metadata) {
        return sendAndWait(new Message(payload, metadata));
    }

    /**
     * Sends a {@link Request} with metadata and blocks for a response.
     */
    default <R> R sendAndWait(Request<R> payload, Metadata metadata) {
        return sendAndWait((Object) payload, metadata);
    }

    /**
     * Sends a message and blocks for a result with a configurable timeout.
     * <p>
     * Timeout can be customized using {@link Timeout @Timeout} on the payload class.
     */
    @SneakyThrows
    default <R> R sendAndWait(Message message) {
        CompletableFuture<R> future = send(message);
        try {
            Timeout timeout = message.getPayload().getClass().getAnnotation(Timeout.class);
            if (timeout != null) {
                return future.get(timeout.value(), timeout.timeUnit());
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

    /**
     * Closes this gateway and releases any underlying resources.
     */
    void close();
}
