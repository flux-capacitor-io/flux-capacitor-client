/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.IndexUtils.indexFromMillis;
import static io.fluxcapacitor.common.IndexUtils.millisFromIndex;
import static io.fluxcapacitor.common.IndexUtils.timestampFromIndex;
import static java.util.stream.Collectors.toList;

@Slf4j
public class InMemorySchedulingClient extends InMemoryMessageStore implements SchedulingClient {

    private final ConcurrentSkipListMap<Long, String> times = new ConcurrentSkipListMap<>();
    private volatile Clock clock = Clock.systemUTC();

    @Override
    public MessageBatch readAndWait(String consumer, String trackerId, Long previousLastIndex,
                                    ConsumerConfiguration configuration) {
        MessageBatch messageBatch = super.readAndWait(consumer, trackerId, previousLastIndex, configuration);
        List<SerializedMessage> messages = messageBatch.getMessages().stream()
                .filter(m -> times.containsKey(m.getIndex()) && clock.millis() >= millisFromIndex(m.getIndex()))
                .collect(toList());
        Long lastIndex = messages.isEmpty() ? null : messages.get(messages.size() - 1).getIndex();
        if (configuration.getTypeFilter() != null) {
            messages = messages.stream().filter(m -> m.getData().getType().matches(configuration.getTypeFilter()))
                    .collect(toList());
        }
        return new MessageBatch(messageBatch.getSegment(), messages, lastIndex);
    }

    @Override
    protected boolean shouldWait(Map<Long, SerializedMessage> tailMap) {
        long deadline = indexFromMillis(clock.millis());
        return tailMap.isEmpty() || tailMap.keySet().stream().noneMatch(index -> index <= deadline);
    }

    @Override
    public Awaitable storePosition(String consumer, int[] segment, long lastIndex) {
        times.headMap(lastIndex).clear();
        return super.storePosition(consumer, segment, lastIndex);
    }

    @Override
    public Awaitable schedule(ScheduledMessage... schedules) {
        for (ScheduledMessage schedule : schedules) {
            cancelSchedule(schedule.getScheduleId());
            long index = indexFromMillis(schedule.getTimestamp());
            while (times.putIfAbsent(index, schedule.getScheduleId()) != null) {
                index++;
            }
            schedule.getMessage().setIndex(index);
        }
        super.send(Arrays.stream(schedules).map(ScheduledMessage::getMessage).toArray(SerializedMessage[]::new));
        return Awaitable.ready();
    }

    @Override
    public Awaitable cancelSchedule(String scheduleId) {
        times.values().removeIf(s -> s.equals(scheduleId));
        return Awaitable.ready();
    }

    @Override
    public Awaitable send(SerializedMessage... messages) {
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
        Map<Long, String> expiredEntries = times.headMap(indexFromMillis(clock.millis()), true);
        List<Schedule> result = expiredEntries.entrySet().stream().map(e -> {
            SerializedMessage m = getMessage(e.getKey());
            return new Schedule(
                    serializer.deserializeMessages(Stream.of(m), true, MessageType.SCHEDULE)
                            .findFirst().get().getPayload(),
                    m.getMetadata(), e.getValue(), timestampFromIndex(e.getKey()));
        }).collect(toList());
        expiredEntries.clear();
        return result;
    }
}
