package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

import java.beans.ConstructorProperties;
import java.time.Duration;
import java.time.Instant;

@Value
@EqualsAndHashCode(callSuper = true)
public class Schedule extends Message {
    public static String scheduleIdMetadataKey = "$scheduleId";

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

    @Override
    public Schedule withPayload(Object payload) {
        return new Schedule(payload, getMetadata(), identityProvider.nextId(), clock().instant(), scheduleId, deadline);
    }

    @Override
    public Schedule withMetadata(Metadata metadata) {
        return new Schedule(getPayload(), metadata, identityProvider.nextId(), clock().instant(), scheduleId, deadline);
    }

    public Schedule withDeadline(Instant deadline) {
        return new Schedule(getPayload(), getMetadata(), identityProvider.nextId(), clock().instant(), scheduleId,
                            deadline);
    }

    public Schedule reschedule(Duration duration) {
        return withDeadline(this.deadline.plus(duration));
    }
}
