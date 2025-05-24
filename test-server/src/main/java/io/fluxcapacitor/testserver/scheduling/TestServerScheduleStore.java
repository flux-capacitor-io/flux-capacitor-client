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
import io.fluxcapacitor.common.InMemoryTaskScheduler;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.TaskScheduler;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.SerializedSchedule;
import io.fluxcapacitor.common.tracking.MessageStore;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.scheduling.client.InMemoryScheduleStore;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import lombok.Value;
import lombok.experimental.Delegate;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.fluxcapacitor.javaclient.tracking.IndexUtils.indexForCurrentTime;

public class TestServerScheduleStore implements MessageStore, SchedulingClient {
    private final TaskScheduler scheduler = new InMemoryTaskScheduler();
    @Delegate
    private final InMemoryScheduleStore delegate;
    private volatile Deadline upcomingDeadline;

    public TestServerScheduleStore(InMemoryScheduleStore delegate) {
        this.delegate = delegate;
        getBatch(indexForCurrentTime(), 1).stream().findFirst().map(SerializedMessage::getIndex)
                .ifPresent(this::rescheduleNextDeadline);
    }

    @Override
    public synchronized CompletableFuture<Void> schedule(Guarantee guarantee, SerializedSchedule... schedules) {
        long now = FluxCapacitor.currentClock().millis();
        try {
            return delegate.schedule(guarantee, schedules);
        } finally {
            Arrays.stream(schedules).mapToLong(SerializedSchedule::getTimestamp).filter(t -> t > now)
                    .map(IndexUtils::indexFromMillis).findFirst().ifPresent(this::rescheduleNextDeadline);
        }
    }

    @Override
    public List<SerializedMessage> getBatch(Long minIndex, int maxSize) {
        return getBatch(minIndex, maxSize, false);
    }

    @Override
    public synchronized List<SerializedMessage> getBatch(Long minIndex, int maxSize, boolean inclusive) {
        List<SerializedMessage> unfiltered = delegate.getBatch(
                minIndex == null ? -1L : inclusive ? minIndex : minIndex + 1, maxSize);
        List<SerializedMessage> filtered = delegate.getBatch(minIndex, maxSize, inclusive);
        unfiltered.stream().filter(m -> !filtered.contains(m)).map(SerializedMessage::getIndex)
                .min(Comparator.naturalOrder()).ifPresent(this::rescheduleNextDeadline);
        return filtered;
    }

    protected void rescheduleNextDeadline(long nextIndex) {
        var nextDeadline = IndexUtils.millisFromIndex(nextIndex);
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
