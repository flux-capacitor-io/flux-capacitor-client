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

package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;

/**
 * Thrown to signal that the user is not authenticated.
 * <p>
 * This typically occurs when no valid authentication token is present or the session has expired.
 * </p>
 */
public class UnauthenticatedException extends FunctionalException {
    /**
     * Constructs a new {@code UnauthenticatedException} with the specified detail message.
     */
    public UnauthenticatedException(String message) {
        super(message);
    }
}
