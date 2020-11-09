/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class LoggingErrorHandler implements ErrorHandler {

    private final boolean logFunctionalErrors;

    public LoggingErrorHandler() {
        this(true);
    }

    @Override
    public void handleError(Exception error, String errorMessage, Runnable retryFunction) {
        if (!(error instanceof FunctionalException)) {
            log.error("{}. Continuing...", errorMessage, error);
        } else if (logFunctionalErrors) {
            log.warn("{}. Continuing...", errorMessage, error);
        }
    }
}
