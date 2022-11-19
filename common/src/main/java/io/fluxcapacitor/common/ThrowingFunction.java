package io.fluxcapacitor.common;

import lombok.NonNull;

@FunctionalInterface
public interface ThrowingFunction<T, R> {
    R apply(T t) throws Exception;

    default <V> ThrowingFunction<V, R> compose(@NonNull ThrowingFunction<? super V, ? extends T> before) {
        return v -> apply(before.apply(v));
    }

    default <V> ThrowingFunction<T, V> andThen(@NonNull ThrowingFunction<? super R, ? extends V> after) {
        return t -> after.apply(apply(t));
    }
}
