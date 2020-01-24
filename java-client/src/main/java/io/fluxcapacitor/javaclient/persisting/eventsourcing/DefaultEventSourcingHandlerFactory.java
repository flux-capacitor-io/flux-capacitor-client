package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.function.BiFunction;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

@AllArgsConstructor
public class DefaultEventSourcingHandlerFactory implements EventSourcingHandlerFactory {
    
    @SuppressWarnings("Convert2MethodRef")
    private static final BiFunction<Class<?>, List<ParameterResolver<? super DeserializingMessage>>, EventSourcingHandler<?>>
            handlerCache = memoize((c, p) -> new AnnotatedEventSourcingHandler<>(c, p));

    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> EventSourcingHandler<T> forType(Class<?> type) {
        return (AnnotatedEventSourcingHandler<T>) handlerCache.apply(type, parameterResolvers);
    }
}
