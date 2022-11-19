package io.fluxcapacitor.common;

import lombok.NonNull;

@FunctionalInterface
public interface ThrowingBiConsumer<T, U> {

    void accept(T t, U u) throws Exception;

    default ThrowingBiConsumer<T, U> andThen(@NonNull ThrowingBiConsumer<? super T, ? super U> after) {
        return (l, r) -> {
            accept(l, r);
            after.accept(l, r);
        };
    }
}
