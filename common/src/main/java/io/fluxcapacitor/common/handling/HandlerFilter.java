package io.fluxcapacitor.common.handling;

import lombok.NonNull;

import java.lang.reflect.Executable;

@FunctionalInterface
public interface HandlerFilter {
    boolean test(Class<?> ownerType, Executable executable);

    default HandlerFilter and(@NonNull HandlerFilter other) {
        return (o, e) -> test(o, e) && other.test(o, e);
    }

    default HandlerFilter negate() {
        return (o, e) -> !test(o, e);
    }

    default HandlerFilter or(@NonNull HandlerFilter other) {
        return (o, e) -> test(o, e) || other.test(o, e);
    }
}
