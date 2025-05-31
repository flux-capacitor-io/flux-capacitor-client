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
 * Thrown when an authenticated user attempts to access a resource or perform an action for which they lack the required
 * permissions.
 */
public class UnauthorizedException extends FunctionalException {
    /**
     * Constructs a new {@code UnauthorizedException} with the specified detail message.
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
