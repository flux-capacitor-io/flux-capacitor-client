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

package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.publishing.MetricsGateway;
import lombok.AllArgsConstructor;

import java.util.function.Consumer;

/**
 * Logs and publishes cache eviction events as metrics.
 * <p>
 * This class listens for eviction events from a {@link Cache} and forwards them to the configured
 * {@link MetricsGateway}. It is typically used to track cache performance and health over time,
 * especially in distributed or memory-sensitive applications.
 *
 * <p>
 * To activate logging for a particular cache instance, call {@link #register(Cache)}.
 * This will subscribe the logger to that cache’s eviction stream.
 *
 * <pre>
 * {@code
 * Cache cache = ...;
 * CacheEvictionsLogger logger = new CacheEvictionsLogger(FluxCapacitor.get().metricsGateway());
 * logger.register(cache);
 * }
 * </pre>
 *
 * @see CacheEvictionEvent
 * @see Cache#registerEvictionListener(Consumer)
 * @see MetricsGateway
 */
@AllArgsConstructor
public class CacheEvictionsLogger implements Consumer<CacheEviction> {

    private final MetricsGateway metricsGateway;

    /**
     * Registers this logger as an eviction listener for the given {@link Cache}.
     * <p>
     * Eviction events will be reported to the configured {@link MetricsGateway}.
     *
     * @param cache The cache to monitor for evictions.
     * @return A {@link Registration} that can be used to unsubscribe later.
     */
    public Registration register(Cache cache) {
        return cache.registerEvictionListener(this);
    }

    /**
     * Publishes the given cache eviction event to the configured metrics gateway.
     *
     * @param evictionEvent the event to report.
     */
    @Override
    public void accept(CacheEviction evictionEvent) {
        metricsGateway.publish(new CacheEvictionEvent(evictionEvent.getId(), evictionEvent.getReason()));
    }
}
