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

package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.javaclient.tracking.BatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class CacheInvalidatingInterceptor implements BatchInterceptor {
    private final Cache cache;
    private final ThreadLocal<int[]> lastSegment = new ThreadLocal<>();

    @Override
    public Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
        return batch -> {
            if (shouldInvalidateCache(batch.getSegment())) {
                try {
                    cache.invalidateAll();
                } catch (Exception e) {
                    log.error("Failed to invalidate event model cache", e);
                }
            }
            if (batch.getSegment()[0] != batch.getSegment()[1]) {
                lastSegment.set(batch.getSegment());
            }
            consumer.accept(batch);
        };
    }

    private boolean shouldInvalidateCache(int[] newSegment) {
        int[] lastSegment = this.lastSegment.get();
        return lastSegment != null && newSegment[0] != newSegment[1]
                && (newSegment[0] > lastSegment[0] || newSegment[1] < lastSegment[1]);
    }
}
