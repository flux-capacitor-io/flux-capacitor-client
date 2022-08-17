package io.fluxcapacitor.common.reflection;

import java.lang.reflect.Member;
import java.util.function.IntFunction;

public interface MemberInvoker {

    default Object invoke(Object target) {
        return invoke(target, 0, i -> null);
    }

    default Object invoke(Object target, Object param) {
        return invoke(target, 1, i -> param);
    }

    default Object invoke(Object target, Object... params) {
        return invoke(target, params.length, i -> params[i]);
    }

    Object invoke(Object target, int parameterCount, IntFunction<?> parameterProvider);

    Member getMember();
}
