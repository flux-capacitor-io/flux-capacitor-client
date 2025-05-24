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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;

/**
 * Strategy for controlling how applied updates (typically from {@link Apply @Apply} methods)
 * are handled in terms of storage and publication.
 * <p>
 * This strategy determines whether an update is published, stored in the event store, or both.
 * It can be configured at the aggregate level, or overridden per update.
 *
 * @see Apply
 * @see Aggregate
 * @see EventPublication
 */
public enum EventPublicationStrategy {

    /**
     * Inherit the strategy from the enclosing aggregate or global default.
     * <p>
     * If not configured anywhere, the fallback is {@link #STORE_AND_PUBLISH}.
     */
    DEFAULT,

    /**
     * Store the applied update in the event store and also publish it to event handlers.
     * <p>
     * This is the default behavior used for event-sourced aggregates.
     */
    STORE_AND_PUBLISH,

    /**
     * Store the applied update in the event store but do not publish it to event handlers.
     * <p>
     * Useful when updates must be persisted but should not trigger side effects or listeners.
     */
    STORE_ONLY,

    /**
     * Publish the update to event handlers but do not store it in the event store.
     * <p>
     * This disables event sourcing for the update and is typically used for transient projections or
     * side-effect-only operations.
     */
    PUBLISH_ONLY
}
