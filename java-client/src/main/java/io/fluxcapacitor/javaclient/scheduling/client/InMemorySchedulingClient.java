package io.fluxcapacitor.javaclient.scheduling.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.TrackingConfiguration;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.toList;

@Slf4j
public class InMemorySchedulingClient extends InMemoryMessageStore implements SchedulingClient, SupportsTimeTravel {
    
    private final AtomicReference<Clock> clock = new AtomicReference<>(Message.getClock());
    private final ConcurrentSkipListMap<Long, String> times = new ConcurrentSkipListMap<>();

    @Override
    public MessageBatch readAndWait(String consumer, String trackerId, Long previousLastIndex,
                                    TrackingConfiguration configuration) {
        MessageBatch messageBatch = super.readAndWait(consumer, trackerId, previousLastIndex, configuration);
        List<SerializedMessage> messages = messageBatch.getMessages().stream()
                .filter(m -> times.containsKey(m.getIndex()))
                .filter(m -> clock.get().millis() >= m.getIndex())
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
        return tailMap.isEmpty() || tailMap.keySet().stream().noneMatch(index -> index <= clock.get().millis());
    }

    @Override
    public Awaitable storePosition(String consumer, int[] segment, long lastIndex) {
        times.headMap(lastIndex).clear();
        return super.storePosition(consumer, segment, lastIndex);
    }

    @Override
    public Awaitable schedule(ScheduledMessage... schedules) {
        for (ScheduledMessage schedule : schedules) {
            long index = schedule.getTimestamp();
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

    @Override
    @SneakyThrows
    public void useClock(Clock clock) {
        this.clock.set(clock);
    }

    @Override
    @SneakyThrows
    public void advanceTimeBy(Duration duration) {
        if (!duration.isNegative()) {
            clock.updateAndGet(c -> Clock.offset(c, duration));
            synchronized (this) {
                notifyAll();
            }
            Thread.sleep(10); //give read thread time to process
        }
    }

    @Override
    public void advanceTimeTo(Instant timestamp) {
        advanceTimeBy(Duration.between(clock.get().instant(), timestamp));
    }

}
