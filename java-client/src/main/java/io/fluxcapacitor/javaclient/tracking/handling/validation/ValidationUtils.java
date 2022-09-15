/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.Invocation;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.modeling.AssertLegal;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.tracking.handling.DeserializingMessageParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.MessageParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.MetadataParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.ForbidsRole;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.NoOpUserProvider;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresRole;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserParameterResolver;
import lombok.SneakyThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValues;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getMethodAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotations;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.isNullable;
import static java.lang.String.format;
import static java.util.Optional.empty;

public class ValidationUtils {
    public static final Validator defaultValidator = Optional.of(ServiceLoader.load(Validator.class))
            .map(ServiceLoader::iterator).filter(Iterator::hasNext).map(Iterator::next)
            .orElse(Jsr380Validator.createDefault());
    private static final Function<Class<?>, Class<?>[]> validateWithGroups = memoize(type -> {
        ValidateWith annotation = type.getAnnotation(ValidateWith.class);
        if (annotation == null) {
            return new Class<?>[0];
        }
        return annotation.value();
    });

    /*
        Check object validity
     */

    public static Optional<ValidationException> checkValidity(Object object, Class<?>... groups) {
        return checkValidity(object, defaultValidator, groups);
    }

    public static boolean isValid(Object object, Class<?>... groups) {
        return isValid(object, defaultValidator, groups);
    }

    public static void assertValid(Object object, Class<?>... groups) {
        assertValid(object, defaultValidator, groups);
    }

    public static Optional<ValidationException> checkValidity(Object object, Validator validator, Class<?>... groups) {
        if (object instanceof Collection<?>) {
            return ((Collection<?>) object).stream().map(
                    o -> checkValidity(o, validator, groups)).reduce((a, b) -> a.isEmpty() ? b : a).orElse(Optional.empty());
        } else {
            return validator.checkValidity(object, getValidationGroups(object, groups));
        }
    }

    public static boolean isValid(Object object, Validator validator, Class<?>... groups) {
        if (object instanceof Collection<?>) {
            return ((Collection<?>) object).stream().map(
                    o -> isValid(o, validator, groups)).reduce((a, b) -> a && b).orElse(true);
        } else {
            return validator.isValid(object, getValidationGroups(object, groups));
        }
    }

    public static void assertValid(Object object, Validator validator, Class<?>... groups) {
        if (object instanceof Iterable<?>) {
            ((Iterable<?>) object).forEach(o -> assertValid(o, validator, groups));
        } else {
            validator.assertValid(object, getValidationGroups(object, groups));
        }
    }

    private static Class<?>[] getValidationGroups(Object object, Class<?>[] customGroups) {
        if (customGroups.length > 0 || object == null) {
            return customGroups;
        }
        return validateWithGroups.apply(object.getClass());
    }

    /*
        Check command / query legality
     */

    protected static class AssertLegalEntityParameterResolver implements ParameterResolver<Entity<?, ?>> {
        @Override
        public Function<Entity<?, ?>, Object> resolve(Parameter parameter, Annotation methodAnnotation) {
            return entity -> resolve(parameter, entity);
        }

        protected Object resolve(Parameter parameter, Entity<?, ?> entity) {
            if (entity == null) {
                return null;
            }
            Class<?> type = entity.type();
            if (parameter.getType().isAssignableFrom(type) || type.isAssignableFrom(parameter.getType())) {
                return entity.get();
            }
            return resolve(parameter, entity.parent());
        }

        @Override
        public boolean matches(Parameter parameter, Annotation methodAnnotation, Entity<?, ?> entity) {
            if (entity == null) {
                return false;
            }
            Class<?> entityType = entity.type();
            Class<?> parameterType = parameter.getType();
            if (entity.get() == null) {
                if (isNullable(parameter)
                    && (parameterType.isAssignableFrom(entityType) || entityType.isAssignableFrom(parameterType))) {
                    return true;
                }
            } else if (parameterType.isAssignableFrom(entityType)) {
                return true;
            }
            return matches(parameter, methodAnnotation, entity.parent());
        }
    }

    protected static final Function<Class<?>, HandlerInvoker<Entity<?, ?>>> assertLegalInvokerCache =
            memoize(type -> HandlerInspector.inspect(
                    type, Arrays.asList(new DeserializingMessageParameterResolver(),
                                        new MetadataParameterResolver(),
                                        new MessageParameterResolver(),
                                        new UserParameterResolver(NoOpUserProvider.getInstance()),
                                        new AssertLegalEntityParameterResolver()),
                    HandlerConfiguration.builder().methodAnnotation(AssertLegal.class)
                            .handlerFilter((owner, method) -> getMethodAnnotation(method, AssertLegal.class)
                                    .map(a -> !a.afterHandler()).orElse(false))
                            .invokeMultipleMethods(true).build()));

    protected static final Function<Class<?>, HandlerInvoker<Entity<?, ?>>> assertLegalAfterUpdateInvokerCache =
            memoize(type -> HandlerInspector.inspect(
                    type, Arrays.asList(new DeserializingMessageParameterResolver(),
                                        new MetadataParameterResolver(),
                                        new MessageParameterResolver(),
                                        new UserParameterResolver(NoOpUserProvider.getInstance()),
                                        new AssertLegalEntityParameterResolver()),
                    HandlerConfiguration.builder().methodAnnotation(AssertLegal.class)
                            .handlerFilter((owner, method) -> getMethodAnnotation(method, AssertLegal.class)
                                    .map(AssertLegal::afterHandler).orElse(false))
                            .invokeMultipleMethods(true).build()));

    public static <E extends Exception> void assertLegal(Object object, Entity<?, ?> entity) throws E {
        Collection<Object> additionalProperties = assertLegal(object, entity, assertLegalInvokerCache);
        additionalProperties.forEach(p -> assertLegal(p, entity));
        Invocation.whenHandlerCompletes((r, e) -> {
            if (e == null) {
                ValidationUtils.assertLegal(object, entity, assertLegalAfterUpdateInvokerCache);
            }
        });
    }

    private static Collection<Object> assertLegal(
            Object object, Entity<?, ?> entity, Function<Class<?>, HandlerInvoker<Entity<?, ?>>> invokerProvider) {
        Object payload = object instanceof Message ? ((Message) object).getPayload() : object;
        if (payload == null) {
            return Collections.emptyList();
        }
        HandlerInvoker<Entity<?, ?>> invoker = invokerProvider.apply(payload.getClass());
        Collection<Object> additionalProperties = new HashSet<>(getAnnotatedPropertyValues(payload, AssertLegal.class));
        if (invoker.canHandle(payload, entity)) {
            Object additionalObject = invoker.invoke(payload, entity);
            if (additionalObject instanceof List<?>) {
                additionalProperties.addAll((List<?>) additionalObject);
            } else if (additionalObject != null) {
                additionalProperties.add(additionalObject);
            }
        }
        entity.possibleTargets(payload).forEach(
                e -> additionalProperties.addAll(ValidationUtils.assertLegal(payload, e, invokerProvider)));
        return additionalProperties;
    }

    @SuppressWarnings("unchecked")
    public static <E extends Exception> Optional<E> checkLegality(Object payload, Entity<?, ?> entity) {
        try {
            assertLegal(payload, entity);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of((E) e);
        }
    }

    public static boolean isLegal(Object commandOrQuery, Entity<?, ?> entity) {
        return checkLegality(commandOrQuery, entity).isEmpty();
    }

    /*
        Check command / query authorization
     */

    private static final Function<Class<?>, String[]> requiredRolesCache = memoize(
            payloadClass -> getRequiredRoles(getTypeAnnotations(payloadClass)));

    private static final BiFunction<Class<?>, Executable, String[]> requiredRolesForMethodCache = memoize(
            (target, executable) -> Optional.ofNullable(getRequiredRoles(Arrays.asList(executable.getAnnotations())))
                    .orElseGet(() -> getRequiredRoles(getTypeAnnotations(target))));

    public static void assertAuthorized(Class<?> payloadType,
                                        User user) throws UnauthenticatedException, UnauthorizedException {
        String[] requiredRoles = requiredRolesCache.apply(payloadType);
        assertAuthorized(payloadType.getSimpleName(), user, requiredRoles);
    }

    public static Optional<Exception> checkAuthorization(Class<?> payloadType, User user) {
        try {
            assertAuthorized(payloadType, user);
        } catch (Exception e) {
            return Optional.of(e);
        }
        return empty();
    }

    public static boolean isAuthorized(Class<?> payloadType, User user) {
        return checkAuthorization(payloadType, user).isEmpty();
    }

    public static boolean isAuthorized(Class<?> target, Executable method, User user) {
        try {
            assertAuthorized(method.getName(), user, requiredRolesForMethodCache.apply(target, method));
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    protected static void assertAuthorized(String action, User user, String[] requiredRoles) {
        if (requiredRoles != null) {
            if (user == null) {
                throw new UnauthenticatedException(format("%s requires authentication", action));
            }
            List<String> remainingRoles = new ArrayList<>();
            if (Arrays.stream(requiredRoles).filter(r -> {
                if (r.startsWith("!")) {
                    return true;
                }
                remainingRoles.add(r);
                return false;
            }).anyMatch(r -> user.hasRole(r.substring(1)))) {
                throw new UnauthorizedException(
                        format("User %s is unauthorized to execute %s", user.getName(), action));
            }
            if (!remainingRoles.isEmpty() && remainingRoles.stream().noneMatch(user::hasRole)) {
                throw new UnauthorizedException(
                        format("User %s is unauthorized to execute %s", user.getName(), action));
            }
        }
    }

    @SneakyThrows
    protected static String[] getRequiredRoles(Iterable<? extends Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof RequiresRole) {
                return ((RequiresRole) annotation).value();
            }
            if (annotation.annotationType().isAnnotationPresent(RequiresRole.class)) {
                for (Method method : ReflectionUtils.getAllMethods(annotation.annotationType())) {
                    if (method.getName().equalsIgnoreCase("value")) {
                        Object[] result = (Object[]) method.invoke(annotation);
                        return Arrays.stream(result).map(Object::toString).toArray(String[]::new);
                    }
                }
            }
            if (annotation instanceof ForbidsRole) {
                return Arrays.stream(((ForbidsRole) annotation).value()).map(s -> "!" + s).toArray(String[]::new);
            }
            if (annotation.annotationType().isAnnotationPresent(ForbidsRole.class)) {
                for (Method method : ReflectionUtils.getAllMethods(annotation.annotationType())) {
                    if (method.getName().equalsIgnoreCase("value")) {
                        Object[] result = (Object[]) method.invoke(annotation);
                        return Arrays.stream(result).map(Object::toString).map(s -> "!" + s).toArray(String[]::new);
                    }
                }
            }
        }
        return null;
    }


}
