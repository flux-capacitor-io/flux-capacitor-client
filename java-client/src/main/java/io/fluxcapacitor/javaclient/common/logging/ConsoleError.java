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

package io.fluxcapacitor.javaclient.common.logging;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

/**
 * A marker message type published to the Flux error gateway for error-level log entries.
 * <p>
 * The associated metadata includes stack traces, log message details, and context.
 *
 * @see ConsoleWarning
 * @see FluxCapacitorLogbackAppender
 */
@Value
@JsonInclude
public class ConsoleError {
}
