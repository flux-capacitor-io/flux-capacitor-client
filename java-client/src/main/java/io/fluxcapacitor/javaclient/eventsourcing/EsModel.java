package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

import java.util.function.Function;
import java.util.function.Predicate;

public interface EsModel<T> {

    default EsModel<T> apply(Object event) {
        return apply(new Message(event, MessageType.EVENT));
    }

    default EsModel<T> apply(Object event, Metadata metadata) {
        return apply(new Message(event, metadata, MessageType.EVENT));
    }

    EsModel<T> apply(Message message);

    default EsModel<T> apply(Function<T, Message> eventFunction) {
        return apply(eventFunction.apply(get()));
    }

    default <E extends Exception> EsModel<T> ensure(Predicate<T> check, Function<T, E> errorProvider) throws E {
        if (!check.test(get())) {
            throw errorProvider.apply(get());
        }
        return this;
    }

    T get();

    long getSequenceNumber();
}
