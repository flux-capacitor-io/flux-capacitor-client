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

package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.publishing.Timeout;
import io.fluxcapacitor.javaclient.tracking.handling.Request;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Represents a WebSocket session that allows sending messages, requests, pings, and handling session lifecycle actions.
 * This interface provides both default and customizable behaviors for interacting with a WebSocket session.
 */
public interface SocketSession {

    /**
     * Retrieves the unique identifier for the current WebSocket session.
     *
     * @return a string representing the unique session ID for the WebSocket session.
     */
    String sessionId();

    /**
     * Sends a message over the WebSocket session with the specified value, using the {@code Guarantee.NONE} policy.
     *
     * @param value the value of the message to be sent. The value will be serialized and transmitted as bytes.
     */
    default void sendMessage(Object value) {
        sendMessage(value, Guarantee.NONE);
    }

    /**
     * Sends a message over the WebSocket session with the specified delivery guarantee.
     *
     * @param value     the value of the message to be sent. The value will be serialized and transmitted as bytes.
     * @param guarantee the delivery guarantee for the message.
     * @return a {@link CompletableFuture} that completes when the message has been sent or stored, or fails with an
     * exception if that operation fails.
     */
    CompletableFuture<Void> sendMessage(Object value, Guarantee guarantee);

    /**
     * Sends a request and returns a {@link CompletionStage} representing the pending result. This method asynchronously
     * sends * the given request and waits for the corresponding response. The request object will be wrapped by
     * {@link SocketRequest} and then sent over the WebSocket session. Once the session receives a
     * {@link SocketResponse} the request will be completed.
     * <p>
     * The timeout for the request is determined based on the {@link Timeout} annotation on the request class. If no
     * {@link Timeout} annotation is present, a default timeout of 30 seconds is applied.
     *
     * @param <R>     the type of the expected response from the request
     * @param request the request to be sent, encapsulating the type of response expected
     * @return a {@link CompletionStage} representing the asynchronous result of the request
     * @see SocketRequest for an explanation of how the request is wrapped.
     * @see SocketResponse for a description of the expected response.
     */
    default <R> CompletionStage<R> sendRequest(Request<R> request) {
        Timeout timeout = ReflectionUtils.getTypeAnnotation(request.getClass(), Timeout.class);
        Duration duration = timeout == null ? Duration.ofSeconds(30)
                : Duration.of(timeout.value(), timeout.timeUnit().toChronoUnit());
        return sendRequest(request, duration);
    }

    /**
     * Sends a request over the WebSocket session with a specified timeout duration. This method asynchronously sends
     * the given request and waits for the corresponding response. The request object will be wrapped by
     * {@link SocketRequest} and then sent over the WebSocket session. Once the session receives a
     * {@link SocketResponse} the request will be completed.
     *
     * @param <R>     the type of the response expected from the request
     * @param request the request object to be sent
     * @param timeout the timeout duration for the request, after which it will fail if no response is received
     * @return a {@code CompletionStage<R>} that completes with the response of the specified type {@code R} or
     * completes exceptionally if an error occurs or the timeout is exceeded
     * @see SocketRequest for an explanation of how the request is wrapped.
     * @see SocketResponse for a description of the expected response.
     */
    <R> CompletionStage<R> sendRequest(Request<R> request, Duration timeout);

    /**
     * Sends a WebSocket ping message with the given value. The ping will be sent without any specific delivery
     * guarantees.
     *
     * @param value the object to be sent as a ping message, which will be serialized and transmitted over the
     *              WebSocket
     */
    default void sendPing(Object value) {
        sendPing(value, Guarantee.NONE);
    }

    /**
     * Sends a ping message with the specified value to the WebSocket session.
     *
     * @param value     the value to be included in the ping message. This can be any object that is serializable or
     *                  manageable according to the WebSocket session's implementation.
     * @param guarantee the delivery guarantee for the ping message. It specifies how assurances (e.g., sending,
     *                  storing) are handled for the message delivery.
     * @return a CompletableFuture that completes once the ping message is processed or the delivery guarantee has been
     * fulfilled.
     */
    CompletableFuture<Void> sendPing(Object value, Guarantee guarantee);

    /**
     * Closes the WebSocket session using the default closing behavior. This method triggers the close operation with a
     * close reason code of 1000 (normal closure) and a {@link Guarantee} of {@code NONE}.
     */
    default void close() {
        close(Guarantee.NONE);
    }

    /**
     * Closes the WebSocket session using the specified close reason and a default guarantee of {@link Guarantee#NONE}.
     *
     * @param closeReason the reason for closing the session, represented as an integer. Standard WebSocket close codes
     *                    can be used (e.g., 1000 for normal closure).
     */
    default void close(int closeReason) {
        close(closeReason, Guarantee.NONE);
    }

    /**
     * Closes the WebSocket session with the specified guarantee for handling pending operations.
     *
     * @param guarantee the level of guarantee to be applied for handling pending messages or pings before closing the
     *                  session. This can be NONE, SENT, or STORED.
     * @return a CompletableFuture that completes when the closing operation is finished.
     */
    default CompletableFuture<Void> close(Guarantee guarantee) {
        return close(1000, guarantee);
    }

    /**
     * Closes the WebSocket session with the specified close reason and delivery guarantee.
     *
     * @param closeReason an integer indicating the reason for closing the WebSocket session, typically based on
     *                    WebSocket close codes (e.g., 1000 for normal closure).
     * @param guarantee   the delivery guarantee for ensuring the closure message is sent, which can be one of the
     *                    {@link Guarantee} values: NONE, SENT, or STORED.
     * @return a {@code CompletableFuture<Void>} that completes when the close operation is performed successfully.
     */
    CompletableFuture<Void> close(int closeReason, Guarantee guarantee);

    /**
     * Determines whether the WebSocket session is currently open.
     *
     * @return true if the session is open; false otherwise.
     */
    boolean isOpen();
}
