package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerNotFoundException;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.List;

import static io.fluxcapacitor.common.handling.HandlerConfiguration.defaultHandlerConfiguration;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;

public class AnnotatedEventSourcingHandler<T> implements EventSourcingHandler<T> {
    
    private final Class<T> handlerType;
    private final HandlerInvoker<DeserializingMessage> invoker;

    public AnnotatedEventSourcingHandler(Class<T> handlerType) {
        this(handlerType, DeserializingMessage.defaultParameterResolvers);
    }

    public AnnotatedEventSourcingHandler(Class<T> handlerType,
                                         List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        this.handlerType = handlerType;
        this.invoker = inspect(handlerType, ApplyEvent.class, parameterResolvers, defaultHandlerConfiguration());
    }

    @Override
    public T invoke(T target, DeserializingMessage message) {
        Object result;
        try {
            result = invoker.invoke(target, message);
        } catch (HandlerNotFoundException e) {
            if (target == null) {
                throw e;
            }
            return target;
        }
        if (target == null) {
            return handlerType.cast(result);
        }
        if (handlerType.isInstance(result)) {
            return handlerType.cast(result);
        }
        if (result == null && invoker.expectResult(target, message)) {
            return null; //this handler has deleted the model on purpose
        }
        return target; //Annotated method returned void - apparently the model is mutable
    }

    @Override
    public boolean canHandle(T target, DeserializingMessage message) {
        return invoker.canHandle(target, message);
    }

}
