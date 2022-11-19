package io.fluxcapacitor.common;

import lombok.NonNull;

@FunctionalInterface
public interface ThrowingBiFunction<T, U, R> {

    R apply(T t, U u) throws Exception;

    default <V> ThrowingBiFunction<T, U, V> andThen(@NonNull ThrowingFunction<? super R, ? extends V> after) {
        return (T t, U u) -> after.apply(apply(t, u));
    }
}
