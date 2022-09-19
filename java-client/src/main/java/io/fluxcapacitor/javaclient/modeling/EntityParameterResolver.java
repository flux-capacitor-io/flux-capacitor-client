package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.DeserializingMessageWithEntity;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.isNullable;

public class EntityParameterResolver implements ParameterResolver<Object> {

    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, Object value) {
        Entity<?> entity = getCurrentEntity(value);
        return matches(parameter, entity)
               && ParameterResolver.super.matches(parameter, methodAnnotation, value);
    }

    protected Entity<?> getCurrentEntity(Object value) {
        Entity<?> entity = null;
        if (value instanceof DeserializingMessageWithEntity) {
            entity = ((DeserializingMessageWithEntity) value).getEntity();
        } else if (value instanceof Entity<?>) {
            entity = ((Entity<?>) value);
        }
        return entity;
    }

    protected boolean matches(Parameter parameter, Entity<?> entity) {
        if (entity == null) {
            return false;
        }
        if (isAssignable(parameter, entity, true)) {
            return true;
        }
        return matches(parameter, entity.parent());
    }

    @Override
    public Function<Object, Object> resolve(Parameter parameter, Annotation methodAnnotation) {
        return m -> resolve(parameter, getCurrentEntity(m)).get();
    }

    protected Supplier<?> resolve(Parameter parameter, Entity<?> entity) {
        if (entity == null) {
            return () -> null;
        }
        if (isAssignable(parameter, entity, false)) {
            return Entity.class.isAssignableFrom(parameter.getType()) ? () -> entity : entity::get;
        }
        return resolve(parameter, entity.parent());
    }

    protected boolean isAssignable(Parameter parameter, Entity<?> entity, boolean considerEntityValue) {
        Class<?> entityType = entity.type();
        Class<?> parameterType = parameter.getType();
        if (!considerEntityValue) {
            return parameterType.isAssignableFrom(entityType) || entityType.isAssignableFrom(parameterType);
        }
        if (entity.get() == null) {
            return isNullable(parameter)
                   && (parameterType.isAssignableFrom(entityType) || entityType.isAssignableFrom(parameterType));
        } else {
            return parameterType.isAssignableFrom(entityType);
        }
    }

    @Override
    public boolean determinesSpecificity() {
        return true;
    }
}
