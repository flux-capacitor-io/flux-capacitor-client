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

import io.fluxcapacitor.common.reflection.DefaultMemberInvoker;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.ForbidsAnyRole;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.ForbidsUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.NoUserRequired;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresAnyRole;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import jakarta.annotation.Nullable;
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
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPackageAndParentPackages;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPackageAnnotations;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotations;
import static java.lang.String.format;
import static java.util.stream.Stream.concat;

/**
 * Utility class providing common validation and authorization routines for message payloads such as commands, queries,
 * and web requests.
 * <p>
 * The {@code ValidationUtils} class supports two primary responsibilities:
 * <ol>
 *     <li><strong>Object validation</strong>: Validates message payloads using a {@link Validator}, optionally
 *     applying validation groups specified via {@link ValidateWith} annotations.</li>
 *     <li><strong>Authorization enforcement</strong>: Performs role-based access checks based on annotations such as
 *     {@link RequiresAnyRole}, {@link ForbidsAnyRole}, and {@link NoUserRequired} declared on classes, methods, or packages.</li>
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
 * @see NoUserRequired
 * @see ValidateWith
 * @see UnauthenticatedException
 * @see UnauthorizedException
 */
@Slf4j
public class ValidationUtils {
    /**
     * Returns the default {@link Validator} used for message validation.
     * <p>
     * This is resolved via Java's {@link ServiceLoader} mechanism. If no custom {@link Validator} is found, a default
     * JSR 380 (Bean Validation) implementation is used.
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
    private static final RequiredRole noUserRequired = new RequiredRole(null, false, false, false);

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
     * @param object    the object to validate
     * @param validator the validator to use
     * @param groups    optional validation groups
     * @return an {@link Optional} containing a {@link ValidationException} if invalid, or empty if valid
     */
    public static Optional<ValidationException> checkValidity(Object object, Validator validator, Class<?>... groups) {
        if (object instanceof Collection<?>) {
            return ((Collection<?>) object).stream().map(
                            o -> checkValidity(o, validator, groups)).reduce((a, b) -> a.isEmpty() ? b : a)
                    .orElse(Optional.empty());
        } else {
            return validator.checkValidity(object, getValidationGroups(object, groups));
        }
    }

    /**
     * Returns {@code true} if the object is valid, using the given {@link Validator} and validation groups.
     *
     * @param object    the object to validate
     * @param validator the validator to use
     * @param groups    optional validation groups
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
     * @param object    the object to validate
     * @param validator the validator to use
     * @param groups    optional validation groups
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

    private static final Function<Class<?>, RequiredRole[]> requiredRolesCache = memoize(
            payloadClass -> Optional.ofNullable(getRequiredRoles(getTypeAnnotations(payloadClass)))
                    .orElseGet(() -> getRequiredRoles(getPackageAnnotations(payloadClass.getPackage()))));

    private static final BiFunction<Class<?>, Executable, RequiredRole[]> requiredRolesForMethodCache = memoize(
            (target, executable) -> Optional.ofNullable(getRequiredRoles(Arrays.asList(executable.getAnnotations())))
                    .or(() -> Optional.ofNullable(getRequiredRoles(getTypeAnnotations(target))))
                    .orElseGet(() -> getPackageAndParentPackages(target.getPackage()).stream()
                            .map(p -> ReflectionUtils.getPackageAnnotations(p, false))
                            .map(ValidationUtils::getRequiredRoles)
                            .filter(Objects::nonNull).findFirst().orElse(null)));

    /**
     * Verifies whether the given user is authorized to issue the given payload, based on roles declared via annotations
     * on the payload's class or package.
     * <p>
     * Returns {@code true} if the user is authorized.
     * <p>
     * If the user is not authorized, either {@code false} is returned or an exception is thrown. Which happens depends
     * on the detected annotation.
     *
     * @param payloadType the class of the payload
     * @param user        the authenticated user (may be null)
     * @return {@code true} if authorized, {@code false} if authorization should fail quietly
     * @throws UnauthenticatedException if authentication is required but the user is {@code null}
     * @throws UnauthorizedException    if the user lacks required roles
     */
    public static boolean assertAuthorized(Class<?> payloadType,
                                           User user) throws UnauthenticatedException, UnauthorizedException {
        return assertAuthorized(payloadType.getSimpleName(), user, requiredRolesCache.apply(payloadType));
    }

    /**
     * Returns {@code true} if the given user is authorized to issue the given payload.
     *
     * @param payloadType the class of the payload
     * @param user        the user to check
     * @return {@code true} if authorized, {@code false} otherwise
     */
    public static boolean isAuthorized(Class<?> payloadType, User user) {
        try {
            return assertAuthorized(payloadType, user);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns {@code true} if the given user is authorized to invoke the given method on the given target.
     * <p>
     * Role requirements may be defined via annotations on the method, class, or package.
     *
     * @param target the class declaring the method
     * @param method the method to check
     * @param user   the user to check
     * @return {@code true} if authorized, {@code false} otherwise
     */
    public static boolean isAuthorized(Class<?> target, Executable method, User user) {
        try {
            return assertAuthorized(method.getName(), user, requiredRolesForMethodCache.apply(target, method));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the given user is authorized to invoke the given method on the given target.
     * <p>
     * Returns {@code true} if the user is authorized.
     * <p>
     * If the user is not authorized, either {@code false} is returned or an exception is thrown. Which happens depends
     * on the detected annotation.
     * <p>
     * Role requirements may be defined via annotations on the method, class, or package.
     *
     * @param target the class declaring the method
     * @param method the method to check
     * @param user   the user to check
     * @return {@code true} if authorized, {@code false} if authorization should fail quietly
     * @throws UnauthenticatedException if authentication is required but the user is {@code null}
     * @throws UnauthorizedException    if the user lacks required roles
     */
    public static boolean assertAuthorized(Class<?> target, Executable method, User user) {
        return assertAuthorized(method.getName(), user, requiredRolesForMethodCache.apply(target, method));
    }

    protected static boolean assertAuthorized(String action, User user, RequiredRole[] requiredRoles) {
        if (requiredRoles == null || Arrays.asList(requiredRoles).contains(noUserRequired)) {
            return true;
        }
        Optional<RequiredRole> forbidsUser = Arrays.stream(requiredRoles).filter(RequiredRole::forbidsUser).findFirst();
        if (forbidsUser.isPresent()) {
            if (user != null) {
                if (forbidsUser.get().throwIfUnauthorized()) {
                    throw new UnauthorizedException("Not allowed for authenticated users");
                }
                return false;
            }
            return true;
        }
        if (user == null) {
            if (Arrays.stream(requiredRoles).anyMatch(RequiredRole::throwIfUnauthorized)) {
                throw new UnauthenticatedException(format("%s requires authentication", action));
            }
            return false;
        }
        if (requiredRoles.length == 0) {
            return true;
        }
        List<RequiredRole> remainingRoles = new ArrayList<>();
        List<RequiredRole> forbiddenRoles = Arrays.stream(requiredRoles).filter(r -> {
            if (r.value() != null) {
                if (r.value().startsWith("!")) {
                    return true;
                }
                remainingRoles.add(r);
            }
            return false;
        }).toList();
        for (RequiredRole r : forbiddenRoles) {
            if (r.value() != null && user.hasRole(r.value().substring(1))) {
                if (r.throwIfUnauthorized()) {
                    throw new UnauthorizedException(
                            format("User %s is unauthorized to execute %s", user.getName(), action));
                }
                return false;
            }
        }
        if (!remainingRoles.isEmpty()) {
            boolean throwIfUnauthorized = false;
            for (RequiredRole remainingRole : remainingRoles) {
                if (user.hasRole(remainingRole.value())) {
                    return true;
                }
                if (remainingRole.throwIfUnauthorized()) {
                    throwIfUnauthorized = true;
                }
            }
            if (throwIfUnauthorized) {
                throw new UnauthorizedException(
                        format("User %s is unauthorized to execute %s", user.getName(), action));
            }
            return false;
        }
        return true;
    }

    protected static RequiredRole[] getRequiredRoles(Collection<? extends Annotation> annotations) {
        return annotations.stream().map(ValidationUtils::getRequiredRoles).filter(Objects::nonNull)
                .reduce((a, b) -> concat(Arrays.stream(a), Arrays.stream(b))
                        .toArray(RequiredRole[]::new)).orElse(null);
    }

    static RequiredRole[] getRequiredRoles(Annotation annotation) {
        if (annotation instanceof RequiresAnyRole a) {
            return Arrays.stream(a.value()).map(r -> new RequiredRole(r, a.throwIfUnauthorized(), true, false))
                    .toArray(RequiredRole[]::new);
        }
        if (annotation instanceof NoUserRequired) {
            return new RequiredRole[]{noUserRequired};
        }
        if (annotation instanceof ForbidsUser a) {
            return new RequiredRole[]{new RequiredRole(null, a.throwIfUnauthorized(), false, true)};
        }
        if (annotation instanceof RequiresUser a) {
            return new RequiredRole[]{new RequiredRole(null, a.throwIfUnauthorized(), true, false)};
        }
        if (annotation.annotationType().isAnnotationPresent(RequiresAnyRole.class)) {
            RequiresAnyRole a = annotation.annotationType().getAnnotation(RequiresAnyRole.class);
            var metaRoles = getRequiredRoles(a);
            if (metaRoles != null && metaRoles.length > 0) {
                return metaRoles;
            }
            boolean throwIfUnauthorized
                    = throwIfUnauthorized(annotation).orElseGet(a::throwIfUnauthorized);

            for (Method method : ReflectionUtils.getAllMethods(annotation.annotationType())) {
                if (method.getName().equalsIgnoreCase("value")) {
                    Object[] result = (Object[]) DefaultMemberInvoker.asInvoker(method).invoke(annotation);
                    return Arrays.stream(result).map(Object::toString)
                            .map(r -> new RequiredRole(r, throwIfUnauthorized, true, false))
                            .toArray(RequiredRole[]::new);
                }
            }
            return new RequiredRole[0];
        }
        if (annotation instanceof ForbidsAnyRole a) {
            return Arrays.stream(a.value()).map(s -> "!" + s).map(s -> new RequiredRole(s, a.throwIfUnauthorized(), true, false))
                    .toArray(RequiredRole[]::new);
        }
        if (annotation.annotationType().isAnnotationPresent(ForbidsAnyRole.class)) {
            ForbidsAnyRole a = annotation.annotationType().getAnnotation(ForbidsAnyRole.class);
            var metaRoles = getRequiredRoles(a);
            if (metaRoles != null && metaRoles.length > 0) {
                return metaRoles;
            }
            boolean throwIfUnauthorized
                    = throwIfUnauthorized(annotation).orElseGet(a::throwIfUnauthorized);

            for (Method method : ReflectionUtils.getAllMethods(annotation.annotationType())) {
                if (method.getName().equalsIgnoreCase("value")) {
                    Object[] result = (Object[]) DefaultMemberInvoker.asInvoker(method).invoke(annotation);
                    return Arrays.stream(result).map(Object::toString).map(s -> "!" + s)
                            .map(r -> new RequiredRole(r, throwIfUnauthorized, true, false))
                            .toArray(RequiredRole[]::new);
                }
            }
            return new RequiredRole[0];
        }
        return null;
    }

    static Optional<Boolean> throwIfUnauthorized(Annotation holder) {
        return ReflectionUtils.getMethod(holder.annotationType(), "throwIfUnauthorized")
                .map(DefaultMemberInvoker::asInvoker)
                .map(m -> (boolean) m.invoke(holder));
    }

    protected record RequiredRole(@Nullable String value, boolean throwIfUnauthorized, boolean requiresUser, boolean forbidsUser) {
    }


}
