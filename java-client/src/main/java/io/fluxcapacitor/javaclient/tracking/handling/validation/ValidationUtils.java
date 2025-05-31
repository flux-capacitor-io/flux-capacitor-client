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

package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.ForbidsAnyRole;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresAnyRole;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresNoUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPackageAndParentPackages;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPackageAnnotations;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotations;
import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.stream.Stream.concat;

/**
 * Utility class providing common validation and authorization routines for message payloads such as commands,
 * queries, and web requests.
 * <p>
 * The {@code ValidationUtils} class supports two primary responsibilities:
 * <ol>
 *     <li><strong>Object validation</strong>: Validates message payloads using a {@link Validator}, optionally
 *     applying validation groups specified via {@link ValidateWith} annotations.</li>
 *     <li><strong>Authorization enforcement</strong>: Performs role-based access checks based on annotations such as
 *     {@link RequiresAnyRole}, {@link ForbidsAnyRole}, and {@link RequiresNoUser} declared on classes, methods, or packages.</li>
 * </ol>
 *
 * <h2>Validation</h2>
 * <p>
 * Validation is typically executed automatically by the {@link ValidatingInterceptor} before invoking handler methods.
 * The default validator implementation is loaded via {@link java.util.ServiceLoader} (e.g. {@code Jsr380Validator}).
 * <p>
 * Methods like {@link #assertValid(Object, Class[])} and {@link #checkValidity(Object, Validator, Class[])}
 * support recursive validation of collections and custom validation groups.
 *
 * <h2>Authorization</h2>
 * <p>
 * The class also performs role-based security checks. Handler methods or message payloads can declare required roles,
 * which are evaluated against the current {@link User}. If authorization fails, the utility throws either
 * {@link UnauthenticatedException} or {@link UnauthorizedException}.
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * ValidationUtils.assertValid(payload); // Validate a single object
 * boolean isValid = ValidationUtils.isValid(payload, MyGroup.class); // Validate with a group
 *
 * User user = ...;
 * ValidationUtils.assertAuthorized(MyCommand.class, user); // Authorization check
 * }</pre>
 *
 * @see Validator
 * @see ValidatingInterceptor
 * @see RequiresAnyRole
 * @see ForbidsAnyRole
 * @see RequiresNoUser
 * @see ValidateWith
 * @see UnauthenticatedException
 * @see UnauthorizedException
 */
@Slf4j
public class ValidationUtils {
    /**
     * Returns the default {@link Validator} used for message validation.
     * <p>
     * This is resolved via Java's {@link ServiceLoader} mechanism. If no custom {@link Validator}
     * is found, a default JSR 380 (Bean Validation) implementation is used.
     */
    public static final Validator defaultValidator = Optional.of(ServiceLoader.load(Validator.class))
            .map(ServiceLoader::iterator).filter(Iterator::hasNext).map(Iterator::next)
            .orElseGet(Jsr380Validator::createDefault);
    private static final Function<Class<?>, Class<?>[]> validateWithGroups = memoize(type -> {
        ValidateWith annotation = type.getAnnotation(ValidateWith.class);
        if (annotation == null) {
            return new Class<?>[0];
        }
        return annotation.value();
    });
    private static final Set<String> requiresNoUserRole = Set.of(RequiresNoUser.RESERVED_ROLE);

    /*
        Check object validity
     */

    /**
     * Checks whether the provided object is valid, using the default {@link Validator} and validation groups.
     *
     * @param object the object to validate
     * @param groups optional validation groups
     * @return an {@link Optional} containing a {@link ValidationException} if validation fails, or empty if valid
     */
    public static Optional<ValidationException> checkValidity(Object object, Class<?>... groups) {
        return checkValidity(object, defaultValidator, groups);
    }

    /**
     * Returns {@code true} if the given object is valid using the default {@link Validator} and validation groups.
     *
     * @param object the object to validate
     * @param groups optional validation groups
     * @return {@code true} if valid, {@code false} otherwise
     */
    public static boolean isValid(Object object, Class<?>... groups) {
        return isValid(object, defaultValidator, groups);
    }

    /**
     * Asserts that the given object is valid, using the default {@link Validator}.
     * <p>
     * Throws a {@link ValidationException} if the object fails validation.
     *
     * @param object the object to validate
     * @param groups optional validation groups
     * @throws ValidationException if validation fails
     */
    public static void assertValid(Object object, Class<?>... groups) {
        assertValid(object, defaultValidator, groups);
    }

    /**
     * Checks whether the provided object is valid using the given {@link Validator} and validation groups.
     *
     * @param object the object to validate
     * @param validator the validator to use
     * @param groups optional validation groups
     * @return an {@link Optional} containing a {@link ValidationException} if invalid, or empty if valid
     */
    public static Optional<ValidationException> checkValidity(Object object, Validator validator, Class<?>... groups) {
        if (object instanceof Collection<?>) {
            return ((Collection<?>) object).stream().map(
                    o -> checkValidity(o, validator, groups)).reduce((a, b) -> a.isEmpty() ? b : a).orElse(Optional.empty());
        } else {
            return validator.checkValidity(object, getValidationGroups(object, groups));
        }
    }

    /**
     * Returns {@code true} if the object is valid, using the given {@link Validator} and validation groups.
     *
     * @param object the object to validate
     * @param validator the validator to use
     * @param groups optional validation groups
     * @return {@code true} if valid, {@code false} otherwise
     */
    public static boolean isValid(Object object, Validator validator, Class<?>... groups) {
        if (object instanceof Collection<?>) {
            return ((Collection<?>) object).stream().map(
                    o -> isValid(o, validator, groups)).reduce((a, b) -> a && b).orElse(true);
        } else {
            return validator.isValid(object, getValidationGroups(object, groups));
        }
    }

    /**
     * Asserts that the object is valid using the given {@link Validator} and validation groups.
     * <p>
     * Throws a {@link ValidationException} if validation fails.
     *
     * @param object the object to validate
     * @param validator the validator to use
     * @param groups optional validation groups
     * @throws ValidationException if validation fails
     */
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
        Check command / query authorization
     */

    private static final Function<Class<?>, String[]> requiredRolesCache = memoize(
            payloadClass -> Optional.ofNullable(getRequiredRoles(getTypeAnnotations(payloadClass)))
                    .orElseGet(() -> getRequiredRoles(getPackageAnnotations(payloadClass.getPackage()))));

    private static final BiFunction<Class<?>, Executable, String[]> requiredRolesForMethodCache = memoize(
            (target, executable) -> Optional.ofNullable(getRequiredRoles(Arrays.asList(executable.getAnnotations())))
                    .or(() -> Optional.ofNullable(getRequiredRoles(getTypeAnnotations(target))))
                    .orElseGet(() -> getPackageAndParentPackages(target.getPackage()).stream()
                            .map(p -> ReflectionUtils.getPackageAnnotations(p, false))
                            .map(ValidationUtils::getRequiredRoles)
                            .filter(Objects::nonNull).findFirst().orElse(null)));

    /**
     * Verifies whether the given user is authorized to issue the given payload, based on roles
     * declared via annotations on the payload's class or package.
     *
     * @param payloadType the class of the payload
     * @param user the authenticated user (may be null)
     * @throws UnauthenticatedException if authentication is required but the user is {@code null}
     * @throws UnauthorizedException if the user lacks required roles
     */
    public static void assertAuthorized(Class<?> payloadType,
                                        User user) throws UnauthenticatedException, UnauthorizedException {
        String[] requiredRoles = requiredRolesCache.apply(payloadType);
        assertAuthorized(payloadType.getSimpleName(), user, requiredRoles);
    }

    /**
     * Checks if the given user is authorized to issue the given payload.
     *
     * @param payloadType the class of the payload
     * @param user the user to check
     * @return an {@link Optional} containing the exception if unauthorized, or empty if authorized
     */
    public static Optional<Exception> checkAuthorization(Class<?> payloadType, User user) {
        try {
            assertAuthorized(payloadType, user);
        } catch (Exception e) {
            return Optional.of(e);
        }
        return empty();
    }

    /**
     * Returns {@code true} if the given user is authorized to issue the given payload.
     *
     * @param payloadType the class of the payload
     * @param user the user to check
     * @return {@code true} if authorized, {@code false} otherwise
     */
    public static boolean isAuthorized(Class<?> payloadType, User user) {
        return checkAuthorization(payloadType, user).isEmpty();
    }

    /**
     * Returns {@code true} if the given user is authorized to invoke the given method on the given target.
     * <p>
     * Role requirements may be defined via annotations on the method, class, or package.
     *
     * @param target the class declaring the method
     * @param method the method to check
     * @param user the user to check
     * @return {@code true} if authorized, {@code false} otherwise
     */
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
            if (Set.of(requiredRoles).equals(requiresNoUserRole)) {
                return;
            }
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

    protected static String[] getRequiredRoles(Collection<? extends Annotation> annotations) {
        return annotations.stream().map(ValidationUtils::getRequiredRoles).filter(Objects::nonNull)
                .reduce((a, b) -> concat(Arrays.stream(a), Arrays.stream(b)).toArray(String[]::new))
                .orElse(null);
    }

    @SneakyThrows
    protected static String[] getRequiredRoles(Annotation annotation) {
        if (annotation instanceof RequiresAnyRole) {
            return ((RequiresAnyRole) annotation).value();
        }
        if (annotation.annotationType().isAnnotationPresent(RequiresAnyRole.class)) {
            var metaRoles = getRequiredRoles(annotation.annotationType().getAnnotation(RequiresAnyRole.class));
            if (metaRoles != null && metaRoles.length > 0) {
                return metaRoles;
            }

            for (Method method : ReflectionUtils.getAllMethods(annotation.annotationType())) {
                if (method.getName().equalsIgnoreCase("value")) {
                    Object[] result = (Object[]) method.invoke(annotation);
                    return Arrays.stream(result).map(Object::toString).toArray(String[]::new);
                }
            }
            return new String[0];
        }
        if (annotation instanceof ForbidsAnyRole) {
            return Arrays.stream(((ForbidsAnyRole) annotation).value()).map(s -> "!" + s).toArray(String[]::new);
        }
        if (annotation.annotationType().isAnnotationPresent(ForbidsAnyRole.class)) {
            for (Method method : ReflectionUtils.getAllMethods(annotation.annotationType())) {
                if (method.getName().equalsIgnoreCase("value")) {
                    Object[] result = (Object[]) method.invoke(annotation);
                    return Arrays.stream(result).map(Object::toString).map(s -> "!" + s).toArray(String[]::new);
                }
            }
            return new String[0];
        }
        return null;
    }


}
