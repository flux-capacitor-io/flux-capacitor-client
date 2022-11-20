package io.fluxcapacitor.common.handling;

import lombok.NonNull;

import java.lang.reflect.Executable;

@FunctionalInterface
public interface MessageFilter<M> {
    boolean test(M message, Executable executable);

    default MessageFilter<M> and(@NonNull MessageFilter<? super M> other) {
        return (m, e) -> test(m, e) && other.test(m, e);
    }

    default MessageFilter<M> negate() {
        return (m, e) -> !test(m, e);
    }

    default MessageFilter<M> or(@NonNull MessageFilter<? super M> other) {
        return (m, e) -> test(m, e) || other.test(m, e);
    }
}
