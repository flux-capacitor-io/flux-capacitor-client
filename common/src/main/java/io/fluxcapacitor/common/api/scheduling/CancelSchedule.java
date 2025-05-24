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

package io.fluxcapacitor.common.api.scheduling;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.Value;

/**
 * Command to cancel a previously scheduled message using its {@link #scheduleId}.
 * <p>
 * This command instructs Flux to remove a scheduled message if it hasn't yet been delivered.
 * Cancellation is idempotent — if the schedule does not exist, the command has no effect.
 * </p>
 *
 * @see io.fluxcapacitor.common.api.scheduling.Schedule
 * @see io.fluxcapacitor.common.api.scheduling.SerializedSchedule
 */
@Value
public class CancelSchedule extends Command {

    /**
     * The ID of the scheduled message to be cancelled.
     */
    String scheduleId;

    /**
     * Delivery guarantee for the cancellation action.
     */
    Guarantee guarantee;

    /**
     * Returns the routing key used for this command. Equals the {@code scheduleId}.
     */
    @Override
    public String routingKey() {
        return scheduleId;
    }
}
