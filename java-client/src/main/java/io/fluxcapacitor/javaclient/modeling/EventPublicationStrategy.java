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

public enum EventPublicationStrategy {
    /**
     * Use the default strategy to publish events. This will be whatever is configured at the aggregate level, or
     * else application level. If the strategy is set to DEFAULT at all of these levels, STORE_AND_PUBLISH will be used.
     */
    DEFAULT,

    /**
     * Store applied events in the event store and also publish events to event handlers. This is the default strategy.
     */
    STORE_AND_PUBLISH,

    /**
     * Only store applied events in the event store. Don't publish to event handlers.
     */
    STORE_ONLY,

    /**
     * Don't store applied events in the event store. Only publish to event handlers. Note that this will prevent the
     * aggregate from (ever) being event sourced.
     */
    PUBLISH_ONLY
}
