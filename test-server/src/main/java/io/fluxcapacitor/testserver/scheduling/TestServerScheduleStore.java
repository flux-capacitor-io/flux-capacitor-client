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

package io.fluxcapacitor.testserver.scheduling;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.scheduling.SerializedSchedule;
import io.fluxcapacitor.common.tracking.InMemoryTaskScheduler;
import io.fluxcapacitor.common.tracking.MessageStore;
import io.fluxcapacitor.common.tracking.TaskScheduler;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.scheduling.client.InMemoryScheduleStore;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Delegate;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class TestServerScheduleStore implements MessageStore, SchedulingClient {
    private final TaskScheduler scheduler = new InMemoryTaskScheduler();
    private volatile Deadline upcomingDeadline;

    @Delegate
    private final InMemoryScheduleStore delegate;

    @Override
    public synchronized CompletableFuture<Void> schedule(Guarantee guarantee, SerializedSchedule... schedules) {
        long now = FluxCapacitor.currentClock().millis();
        try {
            return delegate.schedule(guarantee, schedules);
        } finally {
            Arrays.stream(schedules).mapToLong(SerializedSchedule::getTimestamp).filter(t -> t > now)
                    .findFirst().ifPresent(this::rescheduleNextDeadline);
        }
    }

    protected void rescheduleNextDeadline(long nextDeadline) {
        if (upcomingDeadline == null || nextDeadline < upcomingDeadline.getTimestamp()) {
            if (upcomingDeadline != null) {
                upcomingDeadline.getScheduleToken().cancel();
            }
            Registration token = scheduler.schedule(nextDeadline, () -> {
                upcomingDeadline = null;
                delegate.notifyMonitors();
            });
            upcomingDeadline = new Deadline(nextDeadline, token);
        }
    }

    @Value
    static class Deadline {
        long timestamp;
        Registration scheduleToken;
    }
}
