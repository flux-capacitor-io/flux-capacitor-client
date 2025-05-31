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

package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Value;

/**
 * Wrapper for a serialized command message scheduled for deferred execution.
 * <p>
 * This class is used by the {@link io.fluxcapacitor.javaclient.scheduling.ScheduledCommandHandler} to encapsulate a
 * {@link SerializedMessage} representing a command that should be dispatched at a future point in time.
 *
 * <p>Instances of this class are typically created by the
 * {@link MessageScheduler} via helper methods like {@link io.fluxcapacitor.javaclient.FluxCapacitor#scheduleCommand}.
 *
 * @see io.fluxcapacitor.javaclient.scheduling.ScheduledCommandHandler
 */
@Value
public class ScheduledCommand {
    /**
     * A serialized representation of the original command scheduled for execution at a later time.
     * <p>
     * This field encapsulates a {@link SerializedMessage}, containing the full payload, metadata, and other details
     * necessary for transmitting or persisting the command. It is used to ensure the command can be accurately
     * reconstituted and dispatched when it is due for execution.
     */
    SerializedMessage command;
}
