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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.api.tracking.MessageBatch;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * A {@link BatchInterceptor} specialization that transforms a {@link MessageBatch} before it is passed to the
 * consumer for processing.
 * <p>
 * This interface combines {@link BatchInterceptor} with a {@link BiFunction} contract, allowing implementors to
 * declaratively map a batch in-place:
 * <ul>
 *   <li>Filter out or modify messages within the batch</li>
 *   <li>Augment batch metadata or headers</li>
 *   <li>Replace or rewrap the entire batch</li>
 * </ul>
 *
 * <p><strong>Design:</strong>
 * <ul>
 *   <li>This is a functional interface. Implementors only need to define {@link #apply(MessageBatch, Tracker)}.</li>
 *   <li>The {@link #intercept} method delegates directly to the {@code apply(...)} transformation before passing the result to the next consumer.</li>
 *   <li>Provides a clean entry point for small, functional batch manipulation use cases.</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * MappingBatchInterceptor maskingInterceptor = (batch, tracker) -> {
 *     List<Message> maskedMessages = batch.getMessages().stream()
 *         .map(m -> m.withMetadata(m.getMetadata().and("masked", true)))
 *         .toList();
 *     return batch.withMessages(maskedMessages);
 * };
 *
 * ConsumerConfiguration.builder()
 *     .name("maskedConsumer")
 *     .batchInterceptor(maskingInterceptor)
 *     .build();
 * }</pre>
 *
 * <p><strong>Best Practices:</strong>
 * <ul>
 *   <li>For non-transformational logic (like delaying or thread context management), prefer {@link BatchInterceptor} directly.</li>
 *   <li>Use {@code MappingBatchInterceptor} when your logic results in a modified or filtered batch.</li>
 * </ul>
 *
 * @see BatchInterceptor
 * @see MessageBatch
 * @see Tracker
 */
@FunctionalInterface
public interface MappingBatchInterceptor extends BatchInterceptor, BiFunction<MessageBatch, Tracker, MessageBatch> {

    /**
     * Applies a transformation to the given {@link MessageBatch}, optionally modifying its contents or structure.
     *
     * @param messageBatch the incoming message batch
     * @param tracker the tracker handling the batch
     * @return the transformed batch to be passed to the next consumer
     */
    @Override
    MessageBatch apply(MessageBatch messageBatch, Tracker tracker);

    /**
     * Wraps the batch processing consumer with a transformation step that rewrites the batch before processing.
     */
    @Override
    default Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
        return batch -> consumer.accept(apply(batch, tracker));
    }
}
