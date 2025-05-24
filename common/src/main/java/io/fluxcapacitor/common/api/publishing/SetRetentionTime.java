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

package io.fluxcapacitor.common.api.publishing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Command to set or update the retention period for a message log in the Flux platform.
 * <p>
 * This determines how long messages remain stored before they are eligible for automatic deletion.
 * The retention applies to logs such as events, commands, metrics, etc., depending on where
 * the command is routed.
 * <p>
 * Note: This is a low-level command primarily used for administrative or system configuration purposes.
 *
 * @see io.fluxcapacitor.common.MessageType
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class SetRetentionTime extends Command {

    /**
     * The new retention time in seconds. If {@code null}, the default retention policy is used.
     */
    Long retentionTimeInSeconds;

    /**
     * The delivery guarantee to use when applying the change.
     */
    Guarantee guarantee;
}
