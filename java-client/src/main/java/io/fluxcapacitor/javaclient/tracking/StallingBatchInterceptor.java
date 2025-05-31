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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentTime;

/**
 * A {@link BatchInterceptor} that stalls batch processing until a minimum desired batch size is reached or a timeout occurs.
 * <p>
 * This interceptor helps regulate the trade-off between **throughput** and **latency** by introducing intentional delays
 * when batches are too small. It’s especially useful in cases where:
 * <ul>
 *   <li>Handlers benefit from larger batches (e.g., bulk writes, deduplication, aggregation)</li>
 *   <li>The event rate is low and batching is desirable</li>
 * </ul>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>If the batch size is greater than or equal to {@code desiredBatchSize}, it is processed immediately.</li>
 *   <li>If the batch size is too small:
 *     <ul>
 *       <li>The interceptor delays processing using {@code Thread.sleep} in intervals of {@code retryFrequency}.</li>
 *       <li>Once {@code maximumStallingDuration} has elapsed since the first refusal, the batch is processed regardless of size.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h2>Usage Considerations</h2>
 * <ul>
 *   <li>This interceptor causes blocking in the tracker thread. It is meant for controlled environments where latency can be traded for efficiency.</li>
 *   <li>It is thread-safe and maintains its own internal stall timer across batches using an {@link AtomicReference}.</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * ConsumerConfiguration.builder()
 *     .name("batchedHandler")
 *     .batchInterceptor(StallingBatchInterceptor.builder()
 *         .desiredBatchSize(100)
 *         .maximumStallingDuration(Duration.ofSeconds(30))
 *         .retryFrequency(Duration.ofMillis(500))
 *         .build())
 *     .build();
 * }</pre>
 *
 * <h2>Defaults</h2>
 * <ul>
 *   <li>{@code desiredBatchSize} = 512</li>
 *   <li>{@code maximumStallingDuration} = 60 seconds</li>
 *   <li>{@code retryFrequency} = 1 second</li>
 * </ul>
 *
 * @see BatchInterceptor
 * @see MessageBatch
 */
@Builder
@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class StallingBatchInterceptor implements BatchInterceptor {
    @Builder.Default
    private final int desiredBatchSize = 512;
    @Builder.Default
    @NonNull
    private final Duration maximumStallingDuration = Duration.ofSeconds(60);
    @Builder.Default
    @NonNull
    private final Duration retryFrequency = Duration.ofSeconds(1);

    private final AtomicReference<Instant> firstRefusal = new AtomicReference<>();

    @Override
    public Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
        return b -> {
            if (b.getSize() >= desiredBatchSize || hasPassedDeadline()) {
                consumer.accept(b);
                firstRefusal.set(null);
            } else {
                stall();
            }
        };
    }

    protected boolean hasPassedDeadline() {
        return Optional.ofNullable(firstRefusal.get())
                .filter(f -> !currentTime().isBefore(f.plus(maximumStallingDuration))).isPresent();
    }

    protected void stall() {
        firstRefusal.updateAndGet(f -> f == null ? currentTime() : f);
        try {
            Thread.sleep(retryFrequency.toMillis());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

}
