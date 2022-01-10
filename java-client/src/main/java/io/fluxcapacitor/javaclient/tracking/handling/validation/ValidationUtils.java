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
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.modeling.AggregateRoot;
import io.fluxcapacitor.javaclient.modeling.AssertLegal;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.ForbidsRole;
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
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotations;
import static java.lang.String.format;
import static java.util.Optional.empty;

public class ValidationUtils {
    public static final Validator defaultValidator = Optional.of(ServiceLoader.load(Validator.class))
            .map(ServiceLoader::iterator).filter(Iterator::hasNext).map(Iterator::next)
            .orElse(Jsr380Validator.createDefault());

    /*
        Check object validity
     */

    public static Optional<ValidationException> checkValidity(Object object, Class<?>... groups) {
        return defaultValidator.checkValidity(object, groups);
    }

    public static boolean isValid(Object object, Class<?>... groups) {
        return defaultValidator.isValid(object, groups);
    }

    public static void assertValid(Object object, Class<?>... groups) {
        defaultValidator.assertValid(object, groups);
    }

    public static void assertValid(Object[] objects, Class<?>... groups) {
        Arrays.stream(objects).forEach(o -> assertValid(o, groups));
    }

    public static void assertValid(List<Object> objects, Class<?>... groups) {
        objects.forEach(o -> assertValid(o, groups));
    }

    /*
        Check command / query legality
     */

    protected static class AssertLegalAggregateParameterResolver implements ParameterResolver<AggregateRoot<?>> {
        @Override
        public Function<AggregateRoot<?>, Object> resolve(Parameter parameter,
                                                          Annotation methodAnnotation) {
            return AggregateRoot::get;
        }

        @Override
        public boolean matches(Parameter parameter,
                               Annotation methodAnnotation, AggregateRoot<?> aggregate) {
            return parameter.getType().isAssignableFrom(aggregate.type()) || aggregate.type()
                    .isAssignableFrom(parameter.getType());
        }
    }

    protected static final Function<Class<?>, HandlerInvoker<AggregateRoot<?>>> assertLegalInvokerCache =
            memoize(type -> HandlerInspector.inspect(
                    type, Arrays.asList(new UserParameterResolver(), new AssertLegalAggregateParameterResolver()),
                    HandlerConfiguration.builder()
                            .methodAnnotation(AssertLegal.class).invokeMultipleMethods(true).build()));

    public static <E extends Exception> void assertLegal(Object commandOrQuery, AggregateRoot<?> aggregate) throws E {
        HandlerInvoker<AggregateRoot<?>> invoker = assertLegalInvokerCache.apply(commandOrQuery.getClass());
        if (invoker.canHandle(commandOrQuery, aggregate)) {
            invoker.invoke(commandOrQuery, aggregate);
        }
    }

    @SuppressWarnings("unchecked")
    public static <E extends Exception> Optional<E> checkLegality(Object commandOrQuery, AggregateRoot<?> aggregate) {
        try {
            assertLegal(commandOrQuery, aggregate);
        } catch (Exception e) {
            return Optional.of((E) e);
        }
        return empty();
    }

    public static boolean isLegal(Object commandOrQuery, AggregateRoot<?> aggregate) {
        return checkLegality(commandOrQuery, aggregate).isEmpty();
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
        return !checkAuthorization(payloadType, user).isPresent();
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
