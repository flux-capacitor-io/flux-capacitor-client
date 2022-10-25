/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.SerializedSchedule;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.SCHEDULE;
import static io.fluxcapacitor.javaclient.tracking.IndexUtils.indexFromMillis;
import static io.fluxcapacitor.javaclient.tracking.IndexUtils.millisFromIndex;
import static io.fluxcapacitor.javaclient.tracking.IndexUtils.timestampFromIndex;
import static java.util.stream.Collectors.toList;

@Slf4j
public class InMemorySchedulingClient extends InMemoryMessageStore implements SchedulingClient {

    private final ConcurrentSkipListMap<Long, String> scheduleIdsByIndex = new ConcurrentSkipListMap<>();
    private volatile Clock clock = Clock.systemUTC();

    @Override
    protected Collection<SerializedMessage> filterMessages(Collection<SerializedMessage> messages) {
        long maximumIndex = indexFromMillis(clock.millis());
        return super.filterMessages(messages).stream()
                .filter(m -> m.getIndex() <= maximumIndex && scheduleIdsByIndex.containsKey(m.getIndex()))
                .collect(toList());
    }

    @Override
    public Awaitable schedule(SerializedSchedule schedule, boolean ifAbsent, Guarantee guarantee) {
        if (!ifAbsent || !scheduleIdsByIndex.containsValue(schedule.getScheduleId())) {
            cancelSchedule(schedule.getScheduleId(), guarantee);
            long index = indexFromMillis(schedule.getTimestamp());
            while (scheduleIdsByIndex.putIfAbsent(index, schedule.getScheduleId()) != null) {
                index++;
            }
            schedule.getMessage().setIndex(index);
            super.send(Guarantee.SENT, schedule.getMessage());
        }
        return Awaitable.ready();
    }

    @Override
    public Awaitable cancelSchedule(String scheduleId, Guarantee guarantee) {
        scheduleIdsByIndex.values().removeIf(s -> s.equals(scheduleId));
        return Awaitable.ready();
    }

    @Override
    public SerializedSchedule getSchedule(String scheduleId) {
        return scheduleIdsByIndex.entrySet().stream().filter(e -> scheduleId.equals(e.getValue())).findFirst()
                .map(e -> {
                    SerializedMessage message = getMessage(e.getKey());
                    return new SerializedSchedule(scheduleId, millisFromIndex(e.getKey()), message);
                }).orElse(null);
    }

    @Override
    public Awaitable send(Guarantee guarantee, SerializedMessage... messages) {
        throw new UnsupportedOperationException("Use method #schedule instead");
    }

    public void setClock(@NonNull Clock clock) {
        synchronized (this) {
            this.clock = clock;
            notifyAll();
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public List<Schedule> removeExpiredSchedules(Serializer serializer) {
        Map<Long, String> expiredEntries = scheduleIdsByIndex.headMap(indexFromMillis(clock.millis()), true);
        List<Schedule> result = expiredEntries.entrySet().stream().map(e -> {
            SerializedMessage m = getMessage(e.getKey());
            return new Schedule(
                    serializer.deserializeMessages(Stream.of(m), SCHEDULE).findFirst().get().getPayload(),
                    m.getMetadata(), e.getValue(), timestampFromIndex(e.getKey()));
        }).collect(toList());
        expiredEntries.clear();
        return result;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public List<Schedule> getSchedules(Serializer serializer) {
        return scheduleIdsByIndex.entrySet().stream().map(e -> {
            SerializedMessage m = getMessage(e.getKey());
            return new Schedule(
                    serializer.deserializeMessages(Stream.of(m), SCHEDULE).findFirst().get().getPayload(),
                    m.getMetadata(), e.getValue(), timestampFromIndex(e.getKey()));
        }).collect(toList());
    }
}
