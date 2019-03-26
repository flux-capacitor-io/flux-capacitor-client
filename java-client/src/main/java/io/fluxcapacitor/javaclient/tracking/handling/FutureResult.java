package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.Registration;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;

public class FutureResult<T> {
    
    private final Collection<BiConsumer<? super T, Throwable>> consumers = new CopyOnWriteArraySet<>();
    private final CompletableFuture<T> future = new CompletableFuture<T>().whenComplete((r, e) -> {
        synchronized (consumers) {
            consumers.forEach(c -> c.accept(r, e));
        }
    });
    
    @SuppressWarnings({"UnusedReturnValue"})
    public Registration subscribe(BiConsumer<? super T, Throwable> consumer) {
        synchronized (future) {
            if (future.isDone()) {
                try {
                    T result = future.getNow(null);
                    consumer.accept(result, null);
                } catch (Exception e) {
                    consumer.accept(null, e);
                }
            }
        }
        consumers.add(consumer);
        return () -> consumers.remove(consumer);
    }

    public void complete(T value) {
       future.complete(value); 
    }
    
    public void completeExceptionally(Throwable exception) {
        future.completeExceptionally(exception);
    }    
}
