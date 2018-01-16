package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.IndexUtils;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.javaclient.tracking.InMemoryMessageStore;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

import static io.fluxcapacitor.common.IndexUtils.timeFromIndex;
import static io.fluxcapacitor.common.TimingUtils.isMissedDeadline;
import static java.util.stream.Collectors.toList;

public class InMemorySchedulingClient extends InMemoryMessageStore implements SchedulingClient {

    private final ConcurrentSkipListMap<Long, String> times = new ConcurrentSkipListMap<>();

    @Override
    public MessageBatch read(String consumer, int channel, int maxSize, Duration maxTimeout) {
        MessageBatch messageBatch = super.read(consumer, channel, maxSize, maxTimeout);
        List<SerializedMessage> schedulesPastDeadline = messageBatch.getMessages().stream()
                .filter(m -> times.containsKey(m.getIndex()))
                .filter(m -> isMissedDeadline(timeFromIndex(m.getIndex()))).collect(toList());
        return new MessageBatch(messageBatch.getSegment(), schedulesPastDeadline, schedulesPastDeadline.isEmpty()
                ? null : schedulesPastDeadline.get(schedulesPastDeadline.size() - 1).getIndex());
    }

    @Override
    public void storePosition(String consumer, int[] segment, long lastIndex) {
        times.headMap(lastIndex).clear();
        super.storePosition(consumer, segment, lastIndex);
    }

    @Override
    public Awaitable schedule(ScheduledMessage... schedules) {
        for (ScheduledMessage schedule : schedules) {
            long index = IndexUtils.indexForCurrentTime();
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
}
