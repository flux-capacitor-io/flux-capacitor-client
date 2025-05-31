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

import io.fluxcapacitor.common.ObjectUtils;

import java.util.concurrent.Callable;

/**
 * An interface to handle errors encountered during message tracking and processing, with the ability to retry
 * operations.
 *
 * <p>Several built-in {@code ErrorHandler} implementations are provided for different operational needs:
 *
 * <table border="1">
 *   <caption>Built-in ErrorHandler Implementations</caption>
 *   <thead>
 *     <tr>
 *       <th>Implementation</th>
 *       <th>Retries</th>
 *       <th>Throws</th>
 *       <th>Skips Messages</th>
 *       <th>Logging</th>
 *       <th>Use Case</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@link ThrowingErrorHandler}</td>
 *       <td>No</td>
 *       <td>Always</td>
 *       <td>No</td>
 *       <td>Optional (configurable per type)</td>
 *       <td>Critical systems where any failure must stop processing</td>
 *     </tr>
 *     <tr>
 *       <td>{@link LoggingErrorHandler}</td>
 *       <td>No</td>
 *       <td>Never</td>
 *       <td>Yes</td>
 *       <td>Error (technical), Warn (functional)</td>
 *       <td>Default for general-purpose robust message tracking</td>
 *     </tr>
 *     <tr>
 *       <td>{@link SilentErrorHandler}</td>
 *       <td>No</td>
 *       <td>Never</td>
 *       <td>Yes</td>
 *       <td>Optional (configurable level or silent)</td>
 *       <td>Logging/audit projections, passive consumers</td>
 *     </tr>
 *     <tr>
 *       <td>{@link RetryingErrorHandler}</td>
 *       <td>Yes (configurable)</td>
 *       <td>Optional (on final failure)</td>
 *       <td>Optional (configurable)</td>
 *       <td>Error/Warn on failure and retry attempts</td>
 *       <td>Recoverable transient failures with fallback strategy</td>
 *     </tr>
 *     <tr>
 *       <td>{@link ForeverRetryingErrorHandler}</td>
 *       <td>Yes (infinite)</td>
 *       <td>Never</td>
 *       <td>No</td>
 *       <td>Error/Warn on retries</td>
 *       <td>Guaranteed delivery and strict durability requirements</td>
 *     </tr>
 *   </tbody>
 * </table>
 */
@FunctionalInterface
public interface ErrorHandler {
    /**
     * Handles an error encountered during message processing and provides an option to retry the operation.
     *
     * @param error         the Throwable instance representing the error that occurred
     * @param errorMessage  a descriptive message providing context about the error
     * @param retryFunction a Callable representing the operation to retry in case of failure
     * @return an Object which represents the result of the error handling or retry operation. In case an exception is
     * thrown, tracking will be suspended. In case an error is returned but not thrown, tracking will continue, and the
     * error may be logged as a Result message. Any other return value may be logged as a Result message.
     */
    Object handleError(Throwable error, String errorMessage, Callable<?> retryFunction);

    /**
     * Handles an error encountered during message processing and provides an option to retry the operation. Invoked
     * when the return value of the error handler (even if the return value is an exception) is not relevant.
     *
     * @param error         the Throwable instance representing the error that occurred
     * @param errorMessage  a descriptive message providing context about the error
     * @param retryFunction a Callable representing the operation to retry in case of failure
     */
    default void handleError(Throwable error, String errorMessage, Runnable retryFunction) {
        handleError(error, errorMessage, ObjectUtils.asCallable(retryFunction));
    }
}
