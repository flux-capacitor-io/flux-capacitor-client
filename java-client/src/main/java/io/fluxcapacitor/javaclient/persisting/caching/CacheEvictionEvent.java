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

import lombok.Value;

/**
 * Represents an eviction event from a {@link Cache}.
 * <p>
 * These events are typically emitted when a cached object is removed from memory due to
 * expiration, memory pressure, manual eviction, or cache size constraints.
 * <p>
 * Eviction events are useful for metrics reporting and operational observability, particularly
 * when monitoring cache hit/miss ratios and understanding retention behaviors under load.
 *
 * <p>
 * Emitted events can be published to the metrics log via {@link CacheEvictionsLogger} and
 * consumed downstream for alerts or analysis.
 *
 * @see CacheEvictionsLogger
 * @see io.fluxcapacitor.javaclient.publishing.MetricsGateway
 */
@Value
public class CacheEvictionEvent {
    /**
     * The identifier of the evicted object.
     */
    Object id;
    /**
     * The cause for the eviction.
     */
    Reason reason;

    /**
     * Indicates the cause for a cache entry eviction.
     */
    public enum Reason {
        /**
         * The entry was manually evicted by application logic.
         */
        manual,

        /**
         * The eviction occurred due to exceeding the configured cache size.
         */
        size,

        /**
         * The JVM experienced memory pressure and the cache purged entries to free up space.
         */
        memoryPressure,

        /**
         * The entry expired due to time-based eviction policy.
         */
        expiry
    }
}
