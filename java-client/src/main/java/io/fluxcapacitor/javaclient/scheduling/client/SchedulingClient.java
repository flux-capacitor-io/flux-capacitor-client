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

package io.fluxcapacitor.javaclient.scheduling.client;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.scheduling.SerializedSchedule;

import java.util.concurrent.CompletableFuture;

/**
 * A lower-level client interface for scheduling and cancelling deferred messages (i.e., schedules) in Flux Capacitor.
 * <p>
 * This interface provides the primitives for scheduling logic used internally by
 * {@link io.fluxcapacitor.javaclient.scheduling.MessageScheduler}. It may interface with either:
 * <ul>
 *     <li>The Flux Platform in runtime scenarios, where schedules are persisted in the {@link io.fluxcapacitor.common.MessageType#SCHEDULE} log, or</li>
 *     <li>An in-memory schedule store for testing scenarios, allowing fast and isolated feedback cycles.</li>
 * </ul>
 *
 * <p>
 * Most application developers will not use this interface directly. Instead, they should rely on higher-level scheduling APIs
 * such as {@link io.fluxcapacitor.javaclient.scheduling.MessageScheduler} or static methods like
 * {@code FluxCapacitor.schedule(...)}.
 *
 * <p>
 * A schedule represents a message that will be dispatched at a future time. The {@link SerializedSchedule} class encapsulates
 * the serialized form of these scheduled messages.
 *
 * @see io.fluxcapacitor.javaclient.scheduling.MessageScheduler
 * @see SerializedSchedule
 * @see WebsocketSchedulingClient
 */
public interface SchedulingClient extends AutoCloseable {

    /**
     * Schedule one or more serialized schedules using {@link Guarantee#SENT} as the default delivery guarantee.
     *
     * @param schedules One or more schedules to add.
     * @return A future that completes when the schedules have been sent or persisted (depending on the
     * underlying implementation).
     */
    default CompletableFuture<Void> schedule(SerializedSchedule... schedules) {
        return schedule(Guarantee.SENT, schedules);
    }

    /**
     * Schedule one or more serialized schedules with a specified {@link Guarantee}.
     *
     * @param guarantee Delivery guarantee to apply (e.g., none, sent, stored).
     * @param schedules One or more schedules to register.
     * @return A future that completes when the scheduling is acknowledged.
     */
    CompletableFuture<Void> schedule(Guarantee guarantee, SerializedSchedule... schedules);

    /**
     * Cancel a scheduled message using {@link Guarantee#SENT} as the default guarantee.
     *
     * @param scheduleId The identifier of the schedule to cancel.
     * @return A future that completes when the cancellation request is acknowledged.
     */
    default CompletableFuture<Void> cancelSchedule(String scheduleId) {
        return cancelSchedule(scheduleId, Guarantee.SENT);
    }

    /**
     * Cancel a scheduled message using the provided delivery guarantee.
     *
     * @param scheduleId The identifier of the schedule to cancel.
     * @param guarantee  Delivery guarantee for the cancellation request.
     * @return A future that completes when the cancellation is processed.
     */
    CompletableFuture<Void> cancelSchedule(String scheduleId, Guarantee guarantee);

    /**
     * Checks whether a schedule with the given ID currently exists.
     *
     * @param scheduleId The identifier of the schedule to check.
     * @return {@code true} if a schedule exists for the given ID, {@code false} otherwise.
     */
    default boolean hasSchedule(String scheduleId) {
        return getSchedule(scheduleId) != null;
    }

    /**
     * Retrieves the serialized schedule associated with the given ID.
     *
     * @param scheduleId The ID of the schedule to retrieve.
     * @return The matching {@link SerializedSchedule}, or {@code null} if none is found.
     */
    SerializedSchedule getSchedule(String scheduleId);

    /**
     * Closes this client and releases any underlying resources or tracking registrations.
     */
    @Override
    void close();
}
