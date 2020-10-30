package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerNotFoundException;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerConfiguration.defaultHandlerConfiguration;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;

public class AnnotatedEventSourcingHandler<T> implements EventSourcingHandler<T> {

    private final ThreadLocal<T> currentAggregate = new ThreadLocal<>();
    private final Class<T> handlerType;
    private final HandlerInvoker<DeserializingMessage> aggregateInvoker;
    private final Function<Class<?>, HandlerInvoker<DeserializingMessage>> eventInvokers;

    public AnnotatedEventSourcingHandler(Class<T> handlerType) {
        this(handlerType, DeserializingMessage.defaultParameterResolvers);
    }

    public AnnotatedEventSourcingHandler(Class<T> handlerType,
                                         List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        this.handlerType = handlerType;
        this.aggregateInvoker = inspect(handlerType, ApplyEvent.class, parameterResolvers, defaultHandlerConfiguration());
        this.eventInvokers = memoize(eventType -> {
            List<ParameterResolver<? super DeserializingMessage>> paramResolvers = new ArrayList<>(parameterResolvers);
            paramResolvers.add(0,
                               p -> p.getType().isAssignableFrom(handlerType) ? event -> currentAggregate.get() : null);
            return inspect(eventType, Apply.class, paramResolvers, defaultHandlerConfiguration());
        });
    }

    @Override
    public T invoke(T target, DeserializingMessage message) {
        return message.apply(m -> {
            Object result;
            HandlerInvoker<DeserializingMessage> invoker;
            try {
                currentAggregate.set(target);
                boolean handledByAggregate = aggregateInvoker.canHandle(target, m);
                invoker = handledByAggregate ? aggregateInvoker : eventInvokers.apply(message.getPayloadClass());
                result = invoker.invoke(handledByAggregate ? target : m.getPayload(), m);
            } catch (HandlerNotFoundException e) {
                if (target == null) {
                    throw e;
                }
                return target;
            } finally {
                currentAggregate.remove();
            }
            if (target == null) {
                return handlerType.cast(result);
            }
            if (handlerType.isInstance(result)) {
                return handlerType.cast(result);
            }
            if (result == null && invoker.expectResult(target, m)) {
                return null; //this handler has deleted the model on purpose
            }
            return target; //Annotated method returned void - apparently the model is mutable
        });
    }

    @Override
    public boolean canHandle(T target, DeserializingMessage message) {
        try {
            currentAggregate.set(target);
            return aggregateInvoker.canHandle(target, message)
                    || eventInvokers.apply(message.getPayloadClass()).canHandle(message.getPayload(), message);
        } finally {
            currentAggregate.remove();
        }
    }
}
