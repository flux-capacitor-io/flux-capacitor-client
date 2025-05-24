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

package io.fluxcapacitor.common.api;

import io.fluxcapacitor.common.Guarantee;

/**
 * Base class for commands sent to the Flux platform.
 * <p>
 * A {@code Command} represents a request to perform a state-changing operation on the platform, such as
 * indexing a document, deleting entries, or creating audit trails.
 * <p>
 * All commands inherit from {@link Request} and are assigned a unique {@link #requestId} to support correlation
 * and observability.
 * <p>
 * Each command defines a {@link #getGuarantee()} which indicates how delivery or storage of the command should
 * be handled (e.g. whether to wait until it's stored).
 * <p>
 * Optionally, a {@link #routingKey()} may be provided to direct the command to a specific processing node or
 * partition based on a domain-specific identifier (such as a document or collection ID). This helps with consistent
 * hashing or sharding of work in the platform.
 *
 * @see Guarantee
 * @see Request
 */
public abstract class Command extends Request {

    /**
     * Indicates the delivery guarantee required for this command.
     *
     * @return the {@link Guarantee} level (e.g. {@code STORED}, {@code SENT}, {@code NONE})
     */
    public abstract Guarantee getGuarantee();

    /**
     * Optionally specifies a routing key for this command, which may be used to partition work or apply consistent
     * hashing when processed in the Flux platform.
     *
     * @return a routing key string, or {@code null} if not specified
     */
    public String routingKey() {
        return null;
    }
}
