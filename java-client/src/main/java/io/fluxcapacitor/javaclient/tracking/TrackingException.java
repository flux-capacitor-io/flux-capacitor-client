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

package io.fluxcapacitor.javaclient.tracking;

/**
 * Exception thrown during the initialization of message tracking in Flux Capacitor.
 * <p>
 * This exception indicates a configuration or startup problem when assigning handlers to consumers
 * or when attempting to initiate tracking more than once for the same consumer configuration.
 */
public class TrackingException extends RuntimeException {

    /**
     * Constructs a new {@code TrackingException} with the specified message and cause.
     *
     * @param message the detail message
     * @param cause   the root cause
     */
    public TrackingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code TrackingException} with the specified message.
     *
     * @param message the detail message
     */
    public TrackingException(String message) {
        super(message);
    }
}
