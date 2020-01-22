package io.fluxcapacitor.javaclient.modelling;

import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerInvoker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;
import static java.util.Collections.singletonList;

public class DefaultLegalCheck {
    private static Map<Class<?>, HandlerInvoker<Aggregate<?>>> invokerCache = new ConcurrentHashMap<>();

    public static <E extends Exception> void assertLegal(Object commandOrQuery, Aggregate<?> aggregate) throws E {
        HandlerInvoker<Aggregate<?>> invoker = invokerCache.computeIfAbsent(commandOrQuery.getClass(), type -> inspect(
                commandOrQuery.getClass(), AssertLegal.class, singletonList(
                        p -> p.getDeclaringExecutable().getParameters()[0] == p ? Aggregate::get : null),
                HandlerConfiguration.<Aggregate<?>>builder().failOnMissingMethods(false).invokeMultipleMethods(true)
                        .build()));
        if (invoker.canHandle(commandOrQuery, aggregate)) {
            invoker.invoke(commandOrQuery, aggregate);
        }
    }
}
