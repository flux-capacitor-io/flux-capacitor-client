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

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;

public class ForeverRetryingErrorHandler extends RetryingErrorHandler {

    public ForeverRetryingErrorHandler() {
        this(Duration.ofSeconds(10), e -> !(e instanceof FunctionalException), true, e -> e);
    }

    public ForeverRetryingErrorHandler(Duration delay, Predicate<Throwable> errorFilter, boolean logFunctionalErrors,
                                       Function<Throwable, ?> errorMapper) {
        super(-1, delay, errorFilter, false, logFunctionalErrors, errorMapper);
    }
}
