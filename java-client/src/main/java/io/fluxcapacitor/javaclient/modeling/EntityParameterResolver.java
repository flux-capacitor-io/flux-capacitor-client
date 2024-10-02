/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.HasMessage;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.isNullable;

public class EntityParameterResolver implements ParameterResolver<Object> {

    @Override
    public Function<Object, Object> resolve(Parameter parameter, Annotation methodAnnotation) {
        return m -> resolve(parameter, getMatchingEntity(m, parameter)).get();
    }

    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, Object input) {
        return matches(parameter, getMatchingEntity(input, parameter));
    }

    protected Entity<?> getMatchingEntity(Object input, Parameter parameter) {
        if (input instanceof HasEntity) {
            return ((HasEntity) input).getEntity();
        } else if (input instanceof HasMessage) {
            var type = Entity.getAggregateType((HasMessage) input);
            if (type == null) {
                return null;
            }
            if (Entity.class.isAssignableFrom(parameter.getType())
                || Optional.ofNullable(Entity.getAggregateType((HasMessage) input)).map(
                        t -> parameter.getType().isAssignableFrom(t)).orElse(false)) {
                return Optional.ofNullable(Entity.getAggregateId((HasMessage) input))
                        .or(() -> ((HasMessage) input).computeRoutingKey())
                        .flatMap(possibleEntityId -> FluxCapacitor.getOptionally()
                                .map(fc -> FluxCapacitor.loadEntity(possibleEntityId)))
                        .filter(e -> isAssignable(parameter, e))
                        .filter(e -> e.isPresent() || e.sequenceNumber() > -1L)
                        .orElse(null);
            }
        }
        return null;
    }

    protected boolean matches(Parameter parameter, Entity<?> entity) {
        if (entity == null) {
            return false;
        }
        if (isAssignable(parameter, entity)) {
            return true;
        }
        return matches(parameter, entity.parent());
    }

    protected Supplier<?> resolve(Parameter parameter, Entity<?> entity) {
        if (entity == null) {
            return () -> null;
        }
        if (isAssignable(parameter, entity)) {
            return Entity.class.isAssignableFrom(parameter.getType()) ? () -> entity : entity::get;
        }
        return resolve(parameter, entity.parent());
    }

    protected boolean isAssignable(Parameter parameter, Entity<?> entity) {
        Class<?> eType = entity.type();
        Class<?> pType = getEntityParameterType(parameter);
        return entity.get() == null
                ? (isNullable(parameter) || Entity.class.isAssignableFrom(parameter.getType()))
                  && (pType.isAssignableFrom(eType) || eType.isAssignableFrom(pType))
                : pType.isAssignableFrom(eType);
    }

    private Class<?> getEntityParameterType(Parameter parameter) {
        if (Entity.class.equals(parameter.getType())) {
            Type parameterizedType = parameter.getParameterizedType();
            if (parameterizedType instanceof ParameterizedType) {
                Type[] actualTypeArguments = ((ParameterizedType) parameterizedType).getActualTypeArguments();
                if (actualTypeArguments.length == 1) {
                    Type actualType = actualTypeArguments[0];
                    if (actualType instanceof Class<?>) {
                        return (Class<?>) actualType;
                    } else if (actualType instanceof WildcardType) {
                        Type[] lowerBounds = ((WildcardType) actualType).getLowerBounds();
                        if (lowerBounds.length == 0) {
                            return Object.class;
                        } else {
                            Type lowerBound = lowerBounds[0];
                            if (lowerBound instanceof Class<?>) {
                                return (Class<?>) lowerBound;
                            } else if (lowerBound instanceof ParameterizedType) {
                                lowerBound = ((ParameterizedType) lowerBound).getRawType();
                                if (lowerBound instanceof Class<?>) {
                                    return (Class<?>) lowerBound;
                                }
                            }
                        }
                    }
                }
            }
            return Object.class;
        }
        return parameter.getType();
    }

    @Override
    public boolean determinesSpecificity() {
        return true;
    }
}
