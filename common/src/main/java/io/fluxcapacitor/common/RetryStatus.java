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

import java.time.Instant;

/**
 * Represents the current status of a retry operation.
 * <p>
 * {@code RetryStatus} is used internally by retry utilities (e.g., in {@link TimingUtils}) to track
 * the retry attempt count, the last encountered exception, and timing metadata. It is passed to
 * logging callbacks in {@link RetryConfiguration} for both success and failure cases.
 *
 * @see RetryConfiguration
 * @see TimingUtils#retryOnFailure(java.util.concurrent.Callable, RetryConfiguration)
 */
@Value
@Builder(toBuilder = true)
public class RetryStatus {

    /**
     * The configuration used to govern the retry behavior, including delay, retry limits, and error filters.
     */
    RetryConfiguration retryConfiguration;

    /**
     * The task being retried. Typically a {@link java.util.concurrent.Callable} or {@link Runnable}.
     */
    Object task;

    /**
     * The most recent exception that caused a retry attempt.
     */
    Throwable exception;

    /**
     * The number of times the task has been retried (not including the initial attempt).
     */
    @Default int numberOfTimesRetried = 0;

    /**
     * The timestamp of when the first retryable exception occurred.
     */
    @Default Instant initialErrorTimestamp = Instant.now();

    /**
     * Produces a new {@code RetryStatus} based on this one, incrementing the retry count and updating the exception.
     *
     * @param exception the new exception encountered during retry
     * @return a new {@code RetryStatus} instance with updated retry count and exception
     */
    public RetryStatus afterRetry(Throwable exception) {
        return toBuilder().exception(exception).numberOfTimesRetried(numberOfTimesRetried + 1).build();
    }
}
