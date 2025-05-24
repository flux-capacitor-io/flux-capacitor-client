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

/**
 * Functional interface for handling errors during tasks.
 * <p>
 * The error handler can log the error or escalate the failure depending on the context.
 *
 * @param <T> the type of context associated with the error (e.g., the failed task or message)
 */
@FunctionalInterface
public interface ErrorHandler<T> {

    /**
     * Handles the given {@code Throwable} in the context of the provided value.
     *
     * @param e       the exception that occurred
     * @param context the context or input that caused the exception
     */
    void handleError(Throwable e, T context);
}
