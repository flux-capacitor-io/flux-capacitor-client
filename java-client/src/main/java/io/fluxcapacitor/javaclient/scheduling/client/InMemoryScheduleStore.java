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
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.SerializedSchedule;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.SCHEDULE;
import static io.fluxcapacitor.javaclient.tracking.IndexUtils.indexFromMillis;
import static io.fluxcapacitor.javaclient.tracking.IndexUtils.maxIndexFromMillis;
import static io.fluxcapacitor.javaclient.tracking.IndexUtils.millisFromIndex;
import static io.fluxcapacitor.javaclient.tracking.IndexUtils.timestampFromIndex;
import static java.util.stream.Collectors.toList;

@Slf4j
public class InMemoryScheduleStore extends InMemoryMessageStore implements SchedulingClient {

    private final ConcurrentSkipListMap<Long, Entry> scheduleIdsByIndex = new ConcurrentSkipListMap<>();
    private volatile Clock clock = Clock.systemUTC();

    public InMemoryScheduleStore() {
        super(SCHEDULE);
    }

    public InMemoryScheduleStore(Duration messageExpiration) {
        super(SCHEDULE, messageExpiration);
    }

    @Override
    protected Collection<SerializedMessage> filterMessages(Collection<SerializedMessage> messages) {
        long msThreshold = clock.millis();
        return super.filterMessages(messages).stream()
                .filter(m -> IndexUtils.millisFromIndex(m.getIndex()) <= msThreshold && scheduleIdsByIndex.containsKey(
                        m.getIndex()))
                .collect(toList());
    }

    @Override
    public CompletableFuture<Void> append(SerializedMessage... messages) {
        throw new UnsupportedOperationException();
    }

    @SneakyThrows
    @Override
    public synchronized CompletableFuture<Void> schedule(Guarantee guarantee, SerializedSchedule... schedules) {
        List<SerializedSchedule> filtered = Arrays.stream(schedules)
                .filter(s -> !s.isIfAbsent() || !hasSchedule(s.getScheduleId())).toList();
        long now = FluxCapacitor.currentClock().millis();
        for (SerializedSchedule schedule : filtered) {
            long index = indexFromMillis(Math.max(now, schedule.getTimestamp()));
            while (scheduleIdsByIndex.containsKey(index)) {
                index++;
            }
            Entry entry = new Entry(schedule.getScheduleId());
            cancelSchedule(schedule.getScheduleId());
            schedule.getMessage().setIndex(index);
            if (scheduleIdsByIndex.put(index, entry) != null) {
                log.warn("Overwriting existing schedule with index {}, id {}", index, schedule.getScheduleId());
            }
        }
        return super.append(filtered.stream().map(SerializedSchedule::getMessage).toList());
    }

    @Override
    public synchronized CompletableFuture<Void> cancelSchedule(String scheduleId, Guarantee guarantee) {
        scheduleIdsByIndex.entrySet().stream().filter(e -> scheduleId.equals(e.getValue().getScheduleId()))
                .toList().forEach(e -> scheduleIdsByIndex.computeIfPresent(e.getKey(), (k, v) -> v.invalidate()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public synchronized SerializedSchedule getSchedule(String scheduleId) {
        return scheduleIdsByIndex.entrySet().stream().filter(e -> scheduleId.equals(e.getValue().getScheduleId()))
                .findFirst().filter(e -> e.getValue().isPresent()).map(e -> {
                    SerializedMessage message = getMessage(e.getKey());
                    return new SerializedSchedule(scheduleId, millisFromIndex(e.getKey()), message, false);
                }).orElse(null);
    }

    @Override
    public boolean hasSchedule(String scheduleId) {
        return scheduleIdsByIndex.containsValue(new Entry(scheduleId));
    }

    @Override
    public CompletableFuture<Void> append(List<SerializedMessage> messages) {
        throw new UnsupportedOperationException("Use method #schedule instead");
    }

    public synchronized void setClock(@NonNull Clock clock) {
        synchronized (this) {
            this.clock = clock;
            notifyMonitors();
        }
    }

    public synchronized List<Schedule> getSchedules(Serializer serializer) {
        return asList(scheduleIdsByIndex, serializer);
    }

    public synchronized List<Schedule> removeExpiredSchedules(Serializer serializer) {
        Map<Long, Entry> expiredEntries = scheduleIdsByIndex.headMap(maxIndexFromMillis(clock.millis()), true);
        List<Schedule> result = asList(expiredEntries, serializer);
        expiredEntries.clear();
        return result;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    protected List<Schedule> asList(Map<Long, Entry> scheduleIdsByIndex, Serializer serializer) {
        return scheduleIdsByIndex.entrySet().stream().filter(e -> e.getValue().isPresent()).map(e -> {
            SerializedMessage m = getMessage(e.getKey());
            return new Schedule(
                    serializer.deserializeMessages(Stream.of(m), SCHEDULE).findFirst().get().getPayload(),
                    m.getMetadata(), e.getValue().getScheduleId(), timestampFromIndex(e.getKey()));
        }).toList();
    }

    @Override
    protected void purgeExpiredMessages(Duration messageExpiration) {
        scheduleIdsByIndex.headMap(maxIndexFromMillis(
                clock.millis() - messageExpiration.toMillis()), true).clear();
        super.purgeExpiredMessages(messageExpiration);
    }

    @Override
    public String toString() {
        return "InMemoryScheduleStore";
    }

    @Value
    protected static class Entry {
        String scheduleId;

        public Entry invalidate() {
            return new Entry(null);
        }

        public boolean isPresent() {
            return scheduleId != null;
        }

        public boolean isEmpty() {
            return !isPresent();
        }
    }
}
