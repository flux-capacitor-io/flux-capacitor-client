package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

import java.beans.ConstructorProperties;
import java.time.Clock;
import java.time.Instant;

@Value
@EqualsAndHashCode(callSuper = true)
public class Schedule extends Message {
    @NonNull String scheduleId;
    @NonNull Instant deadline;

    public Schedule(Object payload, String scheduleId, Instant deadline) {
        super(payload);
        this.scheduleId = scheduleId;
        this.deadline = deadline;
    }

    public Schedule(Object payload, Metadata metadata, String scheduleId, Instant deadline) {
        super(payload, metadata);
        this.scheduleId = scheduleId;
        this.deadline = deadline;
    }

    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp", "scheduleId", "deadline"})
    public Schedule(Object payload, Metadata metadata, String messageId, Instant timestamp,
                    String scheduleId, Instant deadline) {
        super(payload, metadata, messageId, timestamp);
        this.scheduleId = scheduleId;
        this.deadline = deadline;
    }

    public boolean isExpired() {
        return isExpired(Message.getClock());
    }

    public boolean isExpired(Clock clock) {
        return !deadline.isAfter(clock.instant());
    }
}
