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

/**
 * Marker interface for objects that carry associated {@link Metadata}.
 * <p>
 * Implementations of this interface expose a structured metadata map that can be
 * used for routing, correlation, trace propagation, or other contextual behavior
 * within the Flux platform.
 * </p>
 *
 * <p>
 * Typical implementers include {@code Message}, {@link SerializedMessage}, and custom types
 * that participate in message tracking or enrichment.
 * </p>
 *
 * @see Metadata
 */
public interface HasMetadata {

    /**
     * Returns the {@link Metadata} associated with this object.
     *
     * @return metadata attached to this instance; never {@code null}
     */
    Metadata getMetadata();
}
