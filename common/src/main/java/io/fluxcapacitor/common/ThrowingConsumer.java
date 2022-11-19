package io.fluxcapacitor.common;

import lombok.NonNull;

@FunctionalInterface
public interface ThrowingConsumer<T> {
    void accept(T t) throws Exception;

    default ThrowingConsumer<T> andThen(@NonNull ThrowingConsumer<? super T> after) {
        return (T t) -> { accept(t); after.accept(t); };
    }
}
