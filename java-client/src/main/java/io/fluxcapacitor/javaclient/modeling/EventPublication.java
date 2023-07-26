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

import io.fluxcapacitor.javaclient.persisting.eventsourcing.InterceptApply;

/**
 * Setting for aggregates to control event publication, used in {@link Aggregate}.
 * <p>
 * Use {@code @Aggregate(eventPublication = IF_MODIFIED)} to stop events from being published if the aggregate does not
 * change, removing the need for dedicated {@link InterceptApply} methods for this.
 * <p>
 * Use {@code @Aggregate(eventPublication = NEVER)} to prevent event publication altogether. This is useful because it
 * allows command application on aggregates with {@code eventSourcing = false} without giving rise to events.
 */
public enum EventPublication {
    ALWAYS, NEVER, IF_MODIFIED;
}
