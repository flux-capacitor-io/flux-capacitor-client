package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerInvoker;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;

public class DefaultLegalCheck {
    private static Map<Class<?>, HandlerInvoker<Object>> invokerCache = new ConcurrentHashMap<>();

    public static <E extends Exception> void assertLegal(Object commandOrQuery, Object aggregate) throws E {
        HandlerInvoker<Object> invoker = invokerCache.computeIfAbsent(commandOrQuery.getClass(), type -> inspect(
                commandOrQuery.getClass(), AssertLegal.class, 
                singletonList(p -> v -> v instanceof Aggregate<?> ? ((Aggregate<?>) v).get() : v),
                HandlerConfiguration.builder().failOnMissingMethods(false).invokeMultipleMethods(true)
                        .build()));
        if (invoker.canHandle(commandOrQuery, aggregate)) {
            invoker.invoke(commandOrQuery, aggregate);
        }
    }

    @SuppressWarnings("unchecked")
    public static <E extends Exception> Optional<E> checkLegality(Object commandOrQuery, Object aggregate) {
        try {
            assertLegal(commandOrQuery, aggregate);
        } catch (Exception e){
            return Optional.of((E) e);
        }
        return empty();
    }

    public static boolean isLegal(Object commandOrQuery, Object aggregate) {
        return !checkLegality(commandOrQuery, aggregate).isPresent();
    }
}
