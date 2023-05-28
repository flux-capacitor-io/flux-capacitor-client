package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerMatcher;
import io.fluxcapacitor.common.handling.Invocation;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.InterceptApply;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.asStream;
import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValues;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotation;

public class DefaultEntityHelper implements EntityHelper {

    private static final Aggregate defaultAggregateAnnotation = DefaultAggregate.class.getAnnotation(Aggregate.class);
    private static final Function<Class<?>, Aggregate> annotationCache = memoize(type -> Optional.ofNullable(
            ReflectionUtils.getTypeAnnotation(type, Aggregate.class)).orElse(defaultAggregateAnnotation));
    public static Aggregate getRootAnnotation(Class<?> type) {
        return annotationCache.apply(type);
    }

    private final Function<Class<?>, HandlerMatcher<Object, HasMessage>> interceptMatchers;
    private final Function<Class<?>, HandlerMatcher<Object, DeserializingMessage>> applyMatchers;
    private final Function<Class<?>, HandlerMatcher<Object, HasMessage>> assertLegalMatchers;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public DefaultEntityHelper(List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        this.interceptMatchers = memoize(type -> inspect(type, (List) parameterResolvers, InterceptApply.class));
        this.applyMatchers = memoize(type -> inspect(type, parameterResolvers, Apply.class));
        this.assertLegalMatchers = memoize(type -> inspect(
                type, (List) parameterResolvers, HandlerConfiguration.builder().methodAnnotation(AssertLegal.class)
                        .invokeMultipleMethods(true).build()));
    }

    @Override
    public Stream<?> intercept(Object value, Entity<?> entity) {
        var m = new MessageWithEntity(value, entity);
        return interceptMatchers.apply(m.getPayloadClass()).findInvoker(m.getPayload(), m)
                .map(i -> asStream(i.invoke()).flatMap(v -> {
                    Message message = Message.asMessage(v);
                    message = message.withMetadata(m.getMetadata().with(message.getMetadata()));
                    if (message.getPayloadClass().equals(m.getPayloadClass())) {
                        return Stream.of(message);
                    }
                    return intercept(message, entity);
                }))
                .orElseGet(() -> Stream.of(value));
    }

    @Override
    public Optional<HandlerInvoker> applyInvoker(DeserializingMessage event, Entity<?> entity) {
        var message = new DeserializingMessageWithEntity(event, entity);
        Class<?> entityType = entity.type();
        return applyMatchers.apply(entityType).findInvoker(entity.get(), message)
                .or(() -> applyMatchers.apply(message.getPayloadClass()).findInvoker(message.getPayload(), message)
                        .filter(i -> {
                            if (i.getMethod() instanceof Method) {
                                Class<?> returnType = ((Method) i.getMethod()).getReturnType();
                                return entityType.isAssignableFrom(returnType)
                                       || returnType.isAssignableFrom(entityType) || returnType.equals(void.class);
                            }
                            return false;
                        }))
                .map(i -> new HandlerInvoker.DelegatingHandlerInvoker(i) {
                    @Override
                    public Object invoke(BiFunction<Object, Object, Object> combiner) {
                        return message.apply(m -> {
                            boolean wasApplying = Entity.isApplying();
                            try {
                                Entity.applying.set(true);
                                Object result = delegate.invoke();
                                if (result == null && !delegate.expectResult()) {
                                    return entity.get(); //Annotated method returned void - apparently the entity is mutable
                                }
                                return result;
                            } finally {
                                Entity.applying.set(wasApplying);
                            }
                        });
                    }
                });
    }

    @Override
    public <E extends Exception> void assertLegal(Object value, Entity<?> entity) throws E {
        assertLegal(value, entity, false);
        Invocation.whenHandlerCompletes((r, e) -> {
            if (e == null) {
                assertLegal(value, entity, true);
            }
        });
    }

    private void assertLegal(Object value, Entity<?> entity, boolean afterHandler) {
        if (value == null) {
            return;
        }
        //check on value
        Object payload = value instanceof HasMessage ? ((HasMessage) value).getPayload() : value;
        assertLegalValue(payload.getClass(), payload, value, entity, afterHandler);
        entity.possibleTargets(payload).forEach(
                e -> assertLegalValue(payload.getClass(), payload, value, e, afterHandler));

        //check on entity
        assertLegalValue(entity.type(), entity.get(), value, entity, afterHandler);
        entity.possibleTargets(payload).forEach(e -> assertLegalValue(e.type(), e.get(), value, e, afterHandler));
    }

    private void assertLegalValue(Class<?> targetType, Object target, Object value, Entity<?> entity,
                                  boolean afterHandler) {
        if (value == null) {
            return;
        }
        MessageWithEntity message = new MessageWithEntity(value, entity);
        Collection<Object> additionalProperties = new HashSet<>(getAnnotatedPropertyValues(target, AssertLegal.class));
        assertLegalMatchers.apply(targetType).findInvoker(target, message)
                .filter(i -> getAnnotation(i.getMethod(), AssertLegal.class).map(
                        a -> a.afterHandler() == afterHandler).orElse(false))
                .ifPresent(s -> {
                    Object additionalObject = s.invoke();
                    if (additionalObject instanceof Collection<?>) {
                        additionalProperties.addAll((Collection<?>) additionalObject);
                    } else {
                        additionalProperties.add(additionalObject);
                    }
                });
        additionalProperties.stream().filter(Objects::nonNull)
                .forEach(p -> assertLegalValue(p.getClass(), p, value, entity, afterHandler));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Exception> Optional<E> checkLegality(Object value, Entity<?> entity) {
        try {
            assertLegal(value, entity);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of((E) e);
        }
    }

    @Override
    public boolean isLegal(Object value, Entity<?> entity) {
        return checkLegality(value, entity).isEmpty();
    }

    @Aggregate
    static class DefaultAggregate {
    }
}
