package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;

import java.time.Instant;

import static java.lang.String.format;

@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
public class EventSourcedModel<T> implements Aggregate<T> {
    String id;
    @Builder.Default long sequenceNumber = -1L;
    String lastEventId;
    @Builder.Default Instant timestamp = Instant.now();
    T model;

    @Override
    public T get() {
        return model;
    }

    @Override
    public Aggregate<T> apply(Message eventMessage) {
        throw new UnsupportedOperationException(format("Not allowed to apply a %s. The model is readonly.",
                                                       eventMessage));
    }
}
