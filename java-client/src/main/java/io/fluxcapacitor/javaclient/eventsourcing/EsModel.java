package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

import java.util.function.Function;

public interface EsModel<T> {

    default EsModel<T> apply(Object event) {
        return apply(new Message(event));
    }

    default EsModel<T> apply(Object event, Metadata metadata) {
        return apply(new Message(event, metadata));
    }

    EsModel<T> apply(Message message);

    default EsModel<T> apply(Function<T, Message> eventFunction) {
        return apply(eventFunction.apply(get()));
    }

    T get();

    long getSequenceNumber();
}
