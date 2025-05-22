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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import io.fluxcapacitor.javaclient.tracking.handling.Request;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Gateway interface for dispatching queries and receiving responses in Flux Capacitor.
 * <p>
 * The {@code QueryGateway} provides a high-level API for submitting queries and retrieving
 * results, either asynchronously or synchronously. It supports rich metadata and integrates with
 * both local and remote query handlers.
 * <p>
 * Queries can be sent as raw payloads, {@link Message} objects, or {@link Request} wrappers for typed responses.
 * This interface also supports registration of local handlers via {@link #registerHandler(Object)}.
 * <p>
 * For message types that are queries, the {@link MessageType#QUERY} enum is typically used.
 *
 * @see HasLocalHandlers
 * @see io.fluxcapacitor.javaclient.tracking.handling.HandleQuery
 */
public interface QueryGateway extends HasLocalHandlers {

    /**
     * Sends the given query asynchronously and returns a future representing the result.
     * <p>
     * If the query is a {@link Message}, it is dispatched as-is. Otherwise, it is wrapped in a new message.
     *
     * @param query the query object
     * @param <R>   the expected type of the result
     * @return a {@link CompletableFuture} with the result
     */
    <R> CompletableFuture<R> send(Object query);

    /**
     * Sends the given query along with metadata asynchronously and returns a future representing the result.
     *
     * @param payload  the query payload
     * @param metadata additional metadata to attach to the message
     * @param <R>      the expected result type
     * @return a {@link CompletableFuture} containing the query result
     */
    <R> CompletableFuture<R> send(Object payload, Metadata metadata);

    /**
     * Sends the given {@link Message} and returns a future representing the resulting message.
     * This method gives access to the full {@link Message} returned by the query handler.
     *
     * @param message the message representing the query
     * @return a {@link CompletableFuture} with the response message
     */
    CompletableFuture<Message> sendForMessage(Message message);

    /**
     * Sends multiple queries asynchronously and returns a list of futures, one for each result.
     *
     * @param messages one or more query objects or messages
     * @param <R>      the expected result type for each query
     * @return a list of {@link CompletableFuture}s for each query result
     */
    <R> List<CompletableFuture<R>> send(Object... messages);

    /**
     * Sends multiple query {@link Message}s and returns a list of futures for the raw responses.
     *
     * @param messages one or more messages representing queries
     * @return a list of {@link CompletableFuture}s containing the result messages
     */
    List<CompletableFuture<Message>> sendForMessages(Message... messages);

    /**
     * Sends the given query and waits for the result, blocking the current thread.
     *
     * @param query the query object
     * @param <R>   the expected result type
     * @return the result of the query
     */
    <R> R sendAndWait(Object query);

    /**
     * Sends the given query and metadata, then waits for the result.
     *
     * @param payload  the query payload
     * @param metadata additional metadata to attach to the query
     * @param <R>      the expected result type
     * @return the result of the query
     */
    <R> R sendAndWait(Object payload, Metadata metadata);

    /**
     * Sends a typed {@link Request} query and returns a future representing the result.
     *
     * @param query the {@link Request} query
     * @param <R>   the expected result type
     * @return a {@link CompletableFuture} containing the result
     */
    <R> CompletableFuture<R> send(Request<R> query);

    /**
     * Sends a typed {@link Request} query with additional metadata and returns a future with the result.
     *
     * @param payload  the {@link Request} payload
     * @param metadata metadata to attach to the request
     * @param <R>      the expected result type
     * @return a {@link CompletableFuture} with the result
     */
    <R> CompletableFuture<R> send(Request<R> payload, Metadata metadata);

    /**
     * Sends a typed {@link Request} query and waits for the result.
     *
     * @param query the {@link Request} query
     * @param <R>   the expected result type
     * @return the result of the query
     */
    <R> R sendAndWait(Request<R> query);

    /**
     * Sends a typed {@link Request} query with metadata and waits for the result.
     *
     * @param payload  the {@link Request} payload
     * @param metadata additional metadata to attach to the query
     * @param <R>      the expected result type
     * @return the result of the query
     */
    <R> R sendAndWait(Request<R> payload, Metadata metadata);

    /**
     * Gracefully shuts down this gateway and releases any held resources.
     */
    void close();
}
