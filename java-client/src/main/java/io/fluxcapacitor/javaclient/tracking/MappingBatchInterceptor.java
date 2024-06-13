package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.api.tracking.MessageBatch;

import java.util.function.BiFunction;
import java.util.function.Consumer;

@FunctionalInterface
public interface MappingBatchInterceptor extends BatchInterceptor, BiFunction<MessageBatch, Tracker, MessageBatch> {
    @Override
    default java.util.function.Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
        return batch -> consumer.accept(apply(batch, tracker));
    }

    @Override
    MessageBatch apply(MessageBatch messageBatch, Tracker tracker);
}
