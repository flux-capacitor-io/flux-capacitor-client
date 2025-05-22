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

package io.fluxcapacitor.javaclient.common.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Exception indicating a technical or unexpected failure within the application.
 * <p>
 * Unlike {@link FunctionalException}, which is designed to communicate business or user-facing errors,
 * {@code TechnicalException} represents low-level, infrastructure, or unforeseen errors such as I/O issues,
 * serialization failures, or unexpected runtime failures.
 * </p>
 *
 * <p>
 * These exceptions are typically used to wrap application exceptions or surface errors that should not be transferred
 * to clients or external systems. However, they are still serialized cleanly (without stack traces) for logging or
 * fallback handling.
 * </p>
 *
 * <p>
 * Stack traces and suppressed exceptions are excluded to prevent information leakage during serialization.
 * </p>
 *
 * @see FunctionalException
 */
@JsonIgnoreProperties({"localizedMessage", "cause", "stackTrace", "suppressed"})
public class TechnicalException extends RuntimeException {

    public TechnicalException() {
        super("An unexpected error occurred");
    }

    public TechnicalException(String message) {
        super(message);
    }

    public TechnicalException(String message, Throwable cause) {
        super(message, cause);
    }

    public TechnicalException(Throwable cause) {
        super(cause);
    }

    public TechnicalException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
