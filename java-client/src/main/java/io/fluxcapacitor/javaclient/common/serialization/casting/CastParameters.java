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

package io.fluxcapacitor.javaclient.common.serialization.casting;

import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Represents metadata that uniquely identifies a version of a serialized data type, along with an optional delta
 * indicating how the revision should change after casting.
 *
 * <p>This class is used by {@link AnnotatedCaster} and related casting infrastructure to determine
 * whether a caster method applies to a given serialized object and to track how the data version should evolve through
 * casting.
 *
 * <p>Example: If a method is annotated to cast from type "Order" revision 1 to revision 2,
 * it would be represented with:
 * <pre>
 * new CastParameters("Order", 1, 1)
 * </pre>
 */
@Value
@Accessors(fluent = true)
public class CastParameters {
    /**
     * the type of the serialized object (typically a fully qualified class name)
     */
    String type;

    /**
     * the original revision this caster applies to
     */
    int revision;

    /**
     * the number of revisions the cast increases (typically 1 for upcasting, and -1 for downcasting).
     */
    int revisionDelta;
}
