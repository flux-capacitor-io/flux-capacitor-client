package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerNotFoundException;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.List;

import static io.fluxcapacitor.common.handling.HandlerConfiguration.defaultHandlerConfiguration;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.defaultParameterResolvers;

public class AnnotatedEventSourcingHandler<T> implements EventSourcingHandler<T> {

    private final Class<T> handlerType;
    private final HandlerInvoker<DeserializingMessage> invoker;

    public AnnotatedEventSourcingHandler(Class<T> handlerType) {
        this(handlerType, defaultParameterResolvers);
    }

    public AnnotatedEventSourcingHandler(Class<T> handlerType,
                                         List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        this.handlerType = handlerType;
        this.invoker = HandlerInspector
                .inspect(handlerType, ApplyEvent.class, parameterResolvers, defaultHandlerConfiguration());
    }

    @Override
    public Class<T> getType() {
        return handlerType;
    }

    @Override
    public T apply(DeserializingMessage message, T model) {
        Object result;
        try {
            result = invoker.invoke(model, message);
        } catch (HandlerNotFoundException e) {
            if (model == null) {
                throw e;
            }
            return model;
        }
        if (model == null) {
            return handlerType.cast(result);
        }
        if (handlerType.isInstance(result)) {
            return handlerType.cast(result);
        }
        if (result == null && invoker.expectResult(model, message)) {
            return null; //this handler has deleted the model on purpose
        }
        return model; //Annotated method returned void - apparently the model is mutable
    }

    @Override
    public boolean canHandle(DeserializingMessage message, T model) {
        return invoker.canHandle(model, message);
    }


}
