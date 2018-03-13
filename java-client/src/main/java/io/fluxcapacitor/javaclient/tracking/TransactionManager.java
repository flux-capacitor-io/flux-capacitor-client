package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.api.tracking.MessageBatch;

import java.util.function.Consumer;

public interface TransactionManager extends BatchInterceptor {

    Transaction startTransaction();

    default void executeInTransaction(Runnable task) {
        Transaction transaction = startTransaction();
        try {
            task.run();
            transaction.commit();
        } catch (Throwable e) {
            transaction.rollback();
            throw e;
        }
    }

    @Override
    default Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
        return messageBatch -> executeInTransaction(() -> consumer.accept(messageBatch));
    }

    interface Transaction {
        void commit();
        void rollback();
    }
}
