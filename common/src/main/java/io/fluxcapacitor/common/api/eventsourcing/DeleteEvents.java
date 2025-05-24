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

package io.fluxcapacitor.common.api.eventsourcing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Command to permanently delete all events associated with a specific aggregate ID.
 * <p>
 * This command removes all persisted events for the given {@code aggregateId} from the event store.
 * It is a <strong>destructive</strong> operation and should only be used for administrative purposes,
 * such as:
 * <ul>
 *   <li>GDPR-compliant data removal</li>
 *   <li>Erasing corrupt or invalid aggregate histories</li>
 *   <li>Manually cleaning up obsolete aggregates</li>
 * </ul>
 *
 * <h2>Important Notes</h2>
 * <ul>
 *   <li>This command <strong>bypasses</strong> typical safety checks and event sourcing protections</li>
 *   <li>The deletion is <strong>not reversible</strong></li>
 *   <li>Snapshots, if any, are <strong>not</strong> deleted by this command</li>
 * </ul>
 *
 * @see AppendEvents
 * @see GetEvents
 * @see EventBatch
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class DeleteEvents extends Command {

    /**
     * The identifier of the aggregate whose events are to be deleted.
     */
    String aggregateId;

    /**
     * Delivery guarantee level for this command.
     */
    Guarantee guarantee;

    /**
     * Returns the routing key used to partition or locate the event stream.
     * This is typically the aggregate ID.
     */
    @Override
    public String routingKey() {
        return aggregateId;
    }
}
