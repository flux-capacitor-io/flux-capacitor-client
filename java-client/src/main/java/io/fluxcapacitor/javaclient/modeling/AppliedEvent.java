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

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.Value;

/**
 * Represents an event that has been applied to an aggregate along with its intended publication strategy.
 *
 * <p>This object combines a deserialized event with its {@link EventPublicationStrategy}, allowing the system
 * to determine how the event should be treated upon commit:
 * <ul>
 *   <li>Whether it should be published immediately to the event gateway</li>
 *   <li>Whether it should only be persisted in the event store of the aggregate</li>
 *   <li>Or both (default behavior)</li>
 * </ul>
 */
@Value
public class AppliedEvent {
    /**
     * The deserialized message representing the applied domain event.
     */
    DeserializingMessage event;

    /**
     * The publication strategy indicating whether this event should be published, stored, or both.
     */
    EventPublicationStrategy publicationStrategy;
}
