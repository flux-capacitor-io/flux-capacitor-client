package io.fluxcapacitor.common.reflection;

import java.lang.reflect.Member;
import java.util.function.IntFunction;

public interface MemberInvoker {
    Object[] emptyArray = new Object[0];

    default Object invoke(Object target) {
        return invoke(target, emptyArray);
    }

    Object invoke(Object target, Object... params);

    Object invoke(Object target, int parameterCount, IntFunction<?> parameterProvider);

    Member getMember();
}
