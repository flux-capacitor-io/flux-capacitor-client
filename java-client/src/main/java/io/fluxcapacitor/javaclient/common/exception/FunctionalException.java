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
import lombok.EqualsAndHashCode;

/**
 * Base class for user-facing exceptions that are intended to be serialized and transferred across system boundaries
 * (e.g., back to a calling client or frontend application).
 * <p>
 * Unlike typical internal exceptions, {@code FunctionalException}s represent errors that are meaningful to the
 * originator of a request — such as validation issues, authentication failures, or rejected commands.
 * </p>
 *
 * <p>
 * These exceptions are explicitly allowed to travel via Flux Platform and may appear in the result of a failed
 * command or query.
 * </p>
 *
 * <p>
 * To support clean serialization and prevent internal leakage, stack traces and suppressed exceptions are excluded.
 * </p>
 */
@EqualsAndHashCode
@JsonIgnoreProperties({"localizedMessage", "cause", "stackTrace", "suppressed"})
public abstract class FunctionalException extends RuntimeException {

    public FunctionalException() {
    }

    public FunctionalException(String message) {
        super(message);
    }

    public FunctionalException(String message, Throwable cause) {
        super(message, cause);
    }

    public FunctionalException(Throwable cause) {
        super(cause);
    }

    public FunctionalException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
