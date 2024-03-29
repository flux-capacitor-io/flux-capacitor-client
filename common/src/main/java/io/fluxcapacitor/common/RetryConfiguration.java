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

package io.fluxcapacitor.common;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

@Value
@Builder(builderClassName = "Builder")
@Slf4j
public class RetryConfiguration {
    @Default
    Duration delay = Duration.ofSeconds(1);
    @Default
    int maxRetries = -1;
    @Default
    Predicate<Exception> errorTest = e -> true;
    @Default
    boolean throwOnFailingErrorTest = false;
    @Default
    Consumer<RetryStatus> successLogger = status -> log.info("Task {} completed successfully on retry", status.getTask());
    @Default
    Consumer<RetryStatus> exceptionLogger = status -> {
        if (status.getNumberOfTimesRetried() == 0) {
            log.error("Task {} failed. Retrying every {} ms...",
                      status.getTask(), status.getRetryConfiguration().getDelay().toMillis(), status.getException());
        } else if (status.getNumberOfTimesRetried() >= status.getRetryConfiguration().getMaxRetries()) {
            log.error("Task {} failed permanently. Not retrying.", status.getTask(), status.getException());
        }
    };
    @Default
    Function<Throwable, ?> errorMapper = e -> e;
}
