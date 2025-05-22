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

package io.fluxcapacitor.javaclient.common.serialization;

import java.util.stream.Stream;

/**
 * Defines the strategy for handling unknown or unresolvable types during deserialization.
 * <p>
 * This is used by {@link Serializer} implementations to determine how to proceed
 * when a type identifier is not recognized (e.g., missing class, renamed type).
 * </p>
 *
 * <ul>
 *   <li>{@link #AS_INTERMEDIATE} – Treat unknown types as intermediate representations.
 *   Deserialization may still proceed later based on structural conversion or inspection.</li>
 *
 *   <li>{@link #IGNORE} – Silently skip unknown types. The deserialized stream will exclude them.</li>
 *
 *   <li>{@link #FAIL} – Immediately throw a {@link DeserializationException} upon encountering an unknown type.</li>
 * </ul>
 *
 * @see Serializer#deserialize(Stream, UnknownTypeStrategy)
 * @see DeserializationException
 */
public enum UnknownTypeStrategy {
    AS_INTERMEDIATE,
    IGNORE,
    FAIL
}
