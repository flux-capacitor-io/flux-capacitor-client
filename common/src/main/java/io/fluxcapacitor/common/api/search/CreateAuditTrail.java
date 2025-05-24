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

package io.fluxcapacitor.common.api.search;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

/**
 * Command to configure to use a search collection as a searchable audit trail.
 * <p>
 * An audit trail ensures that changes to documents are retained for a minimum duration. This is
 * particularly useful for compliance and debugging purposes, as it allows documents to be replayed
 * or inspected after modification or deletion.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class CreateAuditTrail extends Command {

    /**
     * The collection to use as audit trail.
     */
    @NonNull String collection;

    /**
     * The retention period for the audit trail, in seconds. If {@code null}, the default is used.
     */
    Long retentionTimeInSeconds;

    /**
     * The guarantee to use when applying the audit trail configuration.
     */
    Guarantee guarantee;

    /**
     * Uses the collection name as routing key to ensure consistent configuration.
     */
    @Override
    public String routingKey() {
        return collection;
    }
}
