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
 * Thrown to indicate that a problem occurred during the deserialization of data.
 * <p>
 * This exception is typically thrown by a {@link Serializer} implementation when an attempt to parse a serialized
 * representation (e.g. JSON or binary) into a Java object fails.
 * <p>
 * Common scenarios include:
 * <ul>
 *   <li>Invalid or malformed input (e.g. corrupt or truncated JSON)</li>
 *   <li>Missing required fields in the serialized form</li>
 *   <li>Type mismatches or class resolution failures</li>
 * </ul>
 */
public class DeserializationException extends RuntimeException {

    public DeserializationException() {
        super();
    }

    public DeserializationException(String message) {
        super(message);
    }

    public DeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
