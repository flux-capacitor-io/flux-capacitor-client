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

/**
 * Thrown to indicate that a problem occurred during the serialization of an object.
 * <p>
 * This exception typically signals that a {@link Serializer} failed to convert a Java object into its serialized
 * representation (e.g. a JSON string or binary format). This may occur due to unsupported types, serialization
 * misconfigurations, or internal errors in the underlying serialization library.
 *
 * <p>Example causes:
 * <ul>
 *   <li>Attempting to serialize an object with non-serializable fields</li>
 *   <li>Failure in a custom serializer</li>
 *   <li>Misconfigured Jackson modules</li>
 * </ul>
 */
public class SerializationException extends RuntimeException {

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
