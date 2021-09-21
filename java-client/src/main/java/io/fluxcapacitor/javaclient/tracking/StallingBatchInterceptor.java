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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.api.tracking.MessageBatch;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentClock;

@Builder
@Slf4j
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
                .filter(f -> !currentClock().instant().isBefore(f.plus(maximumStallingDuration))).isPresent();
    }

    protected void stall() {
        firstRefusal.updateAndGet(f -> f == null ? currentClock().instant() : f);
        try {
            Thread.sleep(retryFrequency.toMillis());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

}
