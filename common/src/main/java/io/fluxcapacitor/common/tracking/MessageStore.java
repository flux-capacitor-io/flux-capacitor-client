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

package io.fluxcapacitor.common.tracking;

import io.fluxcapacitor.common.Monitored;
import io.fluxcapacitor.common.api.SerializedMessage;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;

/**
 * A low-level store for serialized messages.
 * <p>
 * This interface defines an append-only log used to store {@link SerializedMessage} instances, typically representing
 * commands, events, queries, or other domain messages. It supports batched retrieval and allows integration with
 * in-memory or persistent message tracking implementations.
 * <p>
 * The {@code MessageStore} plays a central role in Flux Capacitor's tracking and message handling infrastructure.
 * In testing, in-memory implementations of {@code MessageStore} are used to simulate Flux platform behavior.
 * <p>
 * This interface is also {@link Monitored}, allowing hooks to observe message publication, and extends
 * {@link HasMessageStore} so it can expose itself as a reusable component.
 *
 * @see SerializedMessage
 * @see Monitored
 * @see HasMessageStore
 */
public interface MessageStore extends AutoCloseable, Monitored<List<SerializedMessage>>, HasMessageStore {

    /**
     * Appends the given messages to the store.
     *
     * @param messages messages to append
     * @return a {@link CompletableFuture} that completes when the messages have been successfully appended
     */
    default CompletableFuture<Void> append(SerializedMessage... messages) {
        return append(asList(messages));
    }

    /**
     * Appends a list of messages to the store.
     *
     * @param messages messages to append
     * @return a {@link CompletableFuture} that completes when the messages have been successfully appended
     */
    CompletableFuture<Void> append(List<SerializedMessage> messages);

    /**
     * Retrieves a batch of messages starting from the given {@code lastIndex} (exclusive).
     *
     * @param lastIndex minimum message index to start from (exclusive)
     * @param maxSize  maximum number of messages to retrieve
     * @return a list of {@link SerializedMessage} instances
     */
    default List<SerializedMessage> getBatch(Long lastIndex, int maxSize) {
        return getBatch(lastIndex, maxSize, false);
    }

    /**
     * Retrieves a batch of messages starting from the given {@code minIndex}.
     *
     * @param minIndex  minimum message index to start from
     * @param maxSize   maximum number of messages to retrieve
     * @param inclusive whether to include the message at {@code minIndex}
     * @return a list of {@link SerializedMessage} instances
     */
    List<SerializedMessage> getBatch(Long minIndex, int maxSize, boolean inclusive);

    /**
     * Sets the retention period for messages. Messages older than this duration may be removed depending on
     * the implementation.
     *
     * @param retentionPeriod duration to retain messages
     */
    void setRetentionTime(Duration retentionPeriod);

    /**
     * Attempts to unwrap the current instance to a concrete implementation or extension of {@code MessageStore}.
     *
     * @param type the desired type to unwrap to
     * @param <T>  the target type
     * @return the unwrapped instance
     * @throws UnsupportedOperationException if the current instance cannot be unwrapped to the given type
     */
    @SuppressWarnings("unchecked")
    default <T extends MessageStore> T unwrap(Class<T> type) {
        if (type.isAssignableFrom(this.getClass())) {
            return (T) this;
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Default no-op close method. Override if resources need cleanup.
     */
    @Override
    default void close() {
        // no-op
    }

    /**
     * Returns the current instance as the {@link MessageStore}.
     */
    @Override
    default MessageStore getMessageStore() {
        return this;
    }
}
