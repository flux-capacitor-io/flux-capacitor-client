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

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Gateway interface for publishing command messages in Flux Capacitor.
 * <p>
 * Commands represent intent—typically something that should change state in the application. They are handled by
 * command handlers (annotated with {@link io.fluxcapacitor.javaclient.tracking.handling.HandleCommand}) and may yield
 * results or raise validation errors.
 * <p>
 * This gateway supports fire-and-forget invocation as well as synchronous and asynchronous sending of commands with or
 * without results. Commands may be published as raw payloads or wrapped
 * {@link io.fluxcapacitor.javaclient.common.Message} objects.
 * <p>
 * Implementations of this interface may also invoke local handlers when available, as exposed via
 * {@link HasLocalHandlers}.
 *
 * @see io.fluxcapacitor.javaclient.tracking.handling.HandleCommand
 */
public interface CommandGateway extends HasLocalHandlers {

    /**
     * Sends a command without waiting for a result.
     *
     * @param command the command to send
     */
    void sendAndForget(Object command);

    /**
     * Sends a command with metadata without waiting for a result.
     *
     * @param payload  the command payload
     * @param metadata metadata to include
     */
    void sendAndForget(Object payload, Metadata metadata);

    /**
     * Sends a command with metadata and delivery guarantee, without waiting for a result.
     *
     * @param payload   the command payload
     * @param metadata  metadata to include
     * @param guarantee the delivery guarantee (e.g., stored before ack)
     */
    void sendAndForget(Object payload, Metadata metadata, Guarantee guarantee);

    /**
     * Sends multiple commands without waiting for results.
     *
     * @param messages the commands to send
     */
    void sendAndForget(Object... messages);

    /**
     * Sends multiple commands with a delivery guarantee without waiting for results.
     *
     * @param guarantee the delivery guarantee
     * @param messages  the commands to send
     * @return a future that completes when all commands have been sent
     */
    CompletableFuture<Void> sendAndForget(Guarantee guarantee, Object... messages);

    /**
     * Sends a command and returns a future for the result.
     *
     * @param command the command to send
     * @param <R>     the expected result type
     * @return a future with the command's result
     */
    <R> CompletableFuture<R> send(Object command);

    /**
     * Sends a command with metadata and returns a future for the result.
     *
     * @param payload  the command payload
     * @param metadata metadata to include
     * @param <R>      the expected result type
     * @return a future with the command's result
     */
    <R> CompletableFuture<R> send(Object payload, Metadata metadata);

    /**
     * Sends a fully-formed {@link Message} and returns a future for the result {@link Message}.
     *
     * @param message the message to send
     * @return a future with the result message
     */
    CompletableFuture<Message> sendForMessage(Message message);

    /**
     * Sends multiple commands and returns futures for their results.
     *
     * @param messages the commands to send
     * @param <R>      the expected result type
     * @return a list of futures with each command's result
     */
    <R> List<CompletableFuture<R>> send(Object... messages);

    /**
     * Sends multiple messages and returns futures for the result messages.
     *
     * @param messages the messages to send
     * @return a list of futures with the resulting messages
     */
    List<CompletableFuture<Message>> sendForMessages(Message... messages);

    /**
     * Sends a command and waits for the result.
     *
     * @param command the command to send
     * @param <R>     the expected result type
     * @return the result of the command
     */
    <R> R sendAndWait(Object command);

    /**
     * Sends a command with metadata and waits for the result.
     *
     * @param payload  the command payload
     * @param metadata metadata to include
     * @param <R>      the expected result type
     * @return the result of the command
     */
    <R> R sendAndWait(Object payload, Metadata metadata);

    /**
     * Sends a typed request command and returns a future for the result.
     *
     * @param query the request command
     * @param <R>   the result type
     * @return a future with the command's result
     */
    <R> CompletableFuture<R> send(Request<R> query);

    /**
     * Sends a typed request command with metadata and returns a future for the result.
     *
     * @param payload  the request command
     * @param metadata metadata to include
     * @param <R>      the result type
     * @return a future with the command's result
     */
    <R> CompletableFuture<R> send(Request<R> payload, Metadata metadata);

    /**
     * Sends a typed request command and waits for the result.
     *
     * @param query the request command
     * @param <R>   the result type
     * @return the result of the command
     */
    <R> R sendAndWait(Request<R> query);

    /**
     * Sends a typed request command with metadata and waits for the result.
     *
     * @param payload  the request command
     * @param metadata metadata to include
     * @param <R>      the result type
     * @return the result of the command
     */
    <R> R sendAndWait(Request<R> payload, Metadata metadata);

    /**
     * Shuts down the command gateway.
     */
    void close();
}
