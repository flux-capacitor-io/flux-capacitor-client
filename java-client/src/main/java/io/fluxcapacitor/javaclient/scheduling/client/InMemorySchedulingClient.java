package io.fluxcapacitor.javaclient.scheduling.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.javaclient.tracking.TrackingConfiguration;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

import static io.fluxcapacitor.common.TimingUtils.isMissedDeadline;
import static java.util.stream.Collectors.toList;

public class InMemorySchedulingClient extends InMemoryMessageStore implements SchedulingClient {

    private final ConcurrentSkipListMap<Long, String> times = new ConcurrentSkipListMap<>();

    @Override
    public MessageBatch readAndWait(String consumer, String trackerId, TrackingConfiguration configuration) {
        MessageBatch messageBatch = super.readAndWait(consumer, trackerId,
                                                      configuration);
        List<SerializedMessage> messages = messageBatch.getMessages().stream()
                .filter(m -> times.containsKey(m.getIndex()))
                .filter(m -> isMissedDeadline(m.getIndex()))
                .collect(toList());
        Long lastIndex = messages.isEmpty() ? null : messages.get(messages.size() - 1).getIndex();
        if (configuration.getTypeFilter() != null) {
            messages = messages.stream().filter(m -> m.getData().getType().matches(configuration.getTypeFilter()))
                    .collect(toList());
        }
        return new MessageBatch(messageBatch.getSegment(), messages, lastIndex);
    }

    @Override
    public Awaitable storePosition(String consumer, int[] segment, long lastIndex) {
        times.headMap(lastIndex).clear();
        return super.storePosition(consumer, segment, lastIndex);
    }

    @Override
    public Awaitable schedule(ScheduledMessage... schedules) {
        for (ScheduledMessage schedule : schedules) {
            long index = System.currentTimeMillis();
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
