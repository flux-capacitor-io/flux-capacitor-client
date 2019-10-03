package io.fluxcapacitor.javaclient.common.model;

import io.fluxcapacitor.common.handling.HandlerInvoker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;
import static java.util.Collections.singletonList;

public class DefaultLegalCheck {
    private static Map<Class<?>, HandlerInvoker<Model<?>>> invokerCache = new ConcurrentHashMap<>();
    
    public static <E extends Exception> void assertLegal(Object commandOrQuery, Model<?> model) throws E {
        HandlerInvoker<Model<?>> invoker = invokerCache.computeIfAbsent(commandOrQuery.getClass(), type -> inspect(
                        commandOrQuery.getClass(), AssertLegal.class, singletonList(
                                p -> p.getDeclaringExecutable().getParameters()[0] == p ? Model::get : null),
                false, true));
        if (invoker.canHandle(commandOrQuery, model)) {
            invoker.invoke(commandOrQuery, model);
        }
    }
}
