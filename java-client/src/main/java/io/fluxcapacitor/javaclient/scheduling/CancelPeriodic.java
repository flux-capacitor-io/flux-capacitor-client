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

package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;

/**
 * Exception used to cancel a periodic schedule from a schedule handler.
 * <p>
 * When thrown from a {@link HandleSchedule} handler that was triggered by a
 * {@link io.fluxcapacitor.javaclient.scheduling.MessageScheduler#schedulePeriodic(Object)} invocation, this exception
 * will signal to the scheduler that the associated periodic schedule should be cancelled permanently.
 * <p>
 * Unlike other exceptions thrown during schedule handling, this will not result in a log warning or error. It is a
 * controlled mechanism for terminating a repeating schedule.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * @HandleSchedule
 * void handle(MyPeriodicMessage message) {
 *     if (shouldStop(message)) {
 *         throw new CancelPeriodic();
 *     }
 *     // process message
 * }
 * }
 * </pre>
 */
public class CancelPeriodic extends RuntimeException {
}
