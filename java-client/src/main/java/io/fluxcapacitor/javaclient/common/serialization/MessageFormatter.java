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

import java.util.function.Function;

/**
 * A functional interface for formatting {@link DeserializingMessage} instances into human-readable strings.
 * <p>
 * This interface is typically used to customize how messages are represented in logs or UI displays. The formatter
 * receives a {@link DeserializingMessage}, which may or may not have been fully deserialized at the time of
 * formatting.
 *
 * <p>
 * Implementations can decide whether to format based on metadata, payload class, or raw type information.
 *
 * <h2>Default Implementation</h2>
 * The {@link #DEFAULT} formatter returns:
 * <ul>
 *   <li>The simple name of the payload class if the message is already deserialized</li>
 *   <li>The type string (usually the fully qualified class name) otherwise</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * String label = MessageFormatter.DEFAULT.apply(message);
 * }</pre>
 */
@FunctionalInterface
public interface MessageFormatter extends Function<DeserializingMessage, String> {

    /**
     * The default formatter that returns the simple name of the payload class if deserialized, or the raw type string
     * if not.
     */
    MessageFormatter DEFAULT = m -> m.isDeserialized() ? m.getPayloadClass().getSimpleName() : m.getType();
}
