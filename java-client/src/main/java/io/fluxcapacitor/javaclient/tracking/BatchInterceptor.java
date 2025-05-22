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
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;

import java.util.List;
import java.util.function.Consumer;

/**
 * Intercepts and decorates batch-level message handling for a {@link Tracker}.
 * <p>
 * A {@code BatchInterceptor} wraps the execution of a {@code Consumer<MessageBatch>}—typically invoked by a tracker to
 * process a group of messages polled from the message log. Interceptors can be used to inject common behavior such as
 * logging, metrics, retries, transaction boundaries, or diagnostics at the batch level.
 * </p>
 *
 * <h2>Usage</h2>
 * <p>
 * Interceptors are applied during consumer configuration via the
 * {@link io.fluxcapacitor.javaclient.tracking.Consumer#batchInterceptors()} attribute, or programmatically. They are
 * composed in a chain using {@link #andThen(BatchInterceptor)} or {@link #join(List)}.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public class LoggingBatchInterceptor implements BatchInterceptor {
 *     @Override
 *     public Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
 *         return batch -> {
 *             log.info("Processing batch of {} messages", batch.size());
 *             consumer.accept(batch);
 *             log.info("Finished processing batch");
 *         };
 *     }
 * }
 * }</pre>
 *
 * @see HandlerInterceptor
 * @see io.fluxcapacitor.javaclient.tracking.Consumer#batchInterceptors()
 */
@FunctionalInterface
public interface BatchInterceptor {

    /**
     * Returns a no-op interceptor that does not alter the consumer behavior.
     */
    static BatchInterceptor noOp() {
        return (c, t) -> c;
    }

    /**
     * Intercepts the given batch message consumer and returns a decorated version to be invoked by the tracker.
     *
     * @param consumer the original consumer that processes the {@link MessageBatch}
     * @param tracker  the tracker invoking this interceptor
     * @return a wrapped consumer with additional behavior
     */
    Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker);

    /**
     * Optional lifecycle callback for cleanup when the tracker shuts down. Default is a no-op.
     *
     * @param tracker the tracker being shut down
     */
    default void shutdown(Tracker tracker) {
        // no-op
    }

    /**
     * Composes this interceptor with another, returning a new interceptor that applies both in sequence. The
     * {@code nextInterceptor} is applied first, followed by this interceptor.
     *
     * @param nextInterceptor the interceptor to apply before this one
     * @return a combined interceptor
     */
    default BatchInterceptor andThen(BatchInterceptor nextInterceptor) {
        return new BatchInterceptor() {
            @Override
            public Consumer<MessageBatch> intercept(Consumer<MessageBatch> c, Tracker t) {
                return BatchInterceptor.this.intercept(nextInterceptor.intercept(c, t), t);
            }

            @Override
            public void shutdown(Tracker tracker) {
                nextInterceptor.shutdown(tracker);
                BatchInterceptor.this.shutdown(tracker);
            }
        };
    }

    /**
     * Joins a list of interceptors into a single composite interceptor, applying them in sequence. If the list is
     * empty, a no-op interceptor is returned.
     *
     * @param interceptors the list of interceptors to join
     * @return a composite interceptor
     */
    static BatchInterceptor join(List<BatchInterceptor> interceptors) {
        return interceptors.stream().reduce(BatchInterceptor::andThen).orElse((c, t) -> c);
    }
}
