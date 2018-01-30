package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerNotFoundException;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.Message;

import java.util.Arrays;
import java.util.List;

public class AnnotatedEventSourcingHandler<T> implements EventSourcingHandler<T> {

    private final Class<T> handlerType;
    private final HandlerInvoker<Message> invoker;

    public AnnotatedEventSourcingHandler(Class<T> handlerType) {
        this(handlerType, Arrays.asList(new PayloadParameterResolver(), new MetadataParameterResolver()));
    }

    public AnnotatedEventSourcingHandler(Class<T> handlerType,
                                         List<ParameterResolver<? super Message>> parameterResolvers) {
        this.handlerType = handlerType;
        this.invoker = HandlerInspector.inspect(handlerType, ApplyEvent.class, parameterResolvers);
    }

    @Override
    public T apply(Message message, T model) {
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
        return model; //apparently the model is mutable
    }


}
