package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.AssertLegal;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresRole;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.SneakyThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerInspector.inspect;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;

public class ValidationUtils {
    public static final Validator defaultValidator = Optional.of(ServiceLoader.load(Validator.class))
            .map(ServiceLoader::iterator).filter(Iterator::hasNext).map(Iterator::next)
            .orElse(Jsr380Validator.createDefault());
    
    /*
        Check object validity
     */

    public static Optional<ValidationException> checkValidity(Object object, Class<?>... group) {
        return defaultValidator.checkValidity(object, group);
    }

    public static boolean isValid(Object object, Class<?>... group) {
        return defaultValidator.isValid(object, group);
    }

    public static void assertValid(Object object, Class<?>... group) {
        defaultValidator.assertValid(object, group);
    }
    
    
    /*
        Check command / query legality
     */

    private static final Function<Class<?>, HandlerInvoker<Object>> assertLegalInvokerCache = memoize(type -> inspect(
            type, AssertLegal.class,
            singletonList(p -> v -> v instanceof Aggregate<?> ? ((Aggregate<?>) v).get() : v),
            HandlerConfiguration.builder().failOnMissingMethods(false).invokeMultipleMethods(true)
                    .build()));

    public static <E extends Exception> void assertLegal(Object commandOrQuery, Object aggregate) throws E {
        HandlerInvoker<Object> invoker = assertLegalInvokerCache.apply(commandOrQuery.getClass());
        if (invoker.canHandle(commandOrQuery, aggregate)) {
            invoker.invoke(commandOrQuery, aggregate);
        }
    }

    @SuppressWarnings("unchecked")
    public static <E extends Exception> Optional<E> checkLegality(Object commandOrQuery, Object aggregate) {
        try {
            assertLegal(commandOrQuery, aggregate);
        } catch (Exception e) {
            return Optional.of((E) e);
        }
        return empty();
    }

    public static boolean isLegal(Object commandOrQuery, Object aggregate) {
        return !checkLegality(commandOrQuery, aggregate).isPresent();
    }
    
    
    /*
        Check command / query authorization
     */
    
    private static final Function<Class<?>, String[]> requiredRolesCache = memoize(ValidationUtils::getRequiredRoles);

    public static void assertAuthorized(Class<?> payloadType, User user) throws UnauthenticatedException, UnauthorizedException {
        String[] requiredRoles = requiredRolesCache.apply(payloadType);
        if (requiredRoles != null) {
            if (user == null) {
                throw new UnauthenticatedException(format("Message %s requires authentication", payloadType));
            }
            if (Arrays.stream(requiredRoles).noneMatch(user::hasRole)) {
                throw new UnauthorizedException(
                        format("User %s is unauthorized to issue %s", user.getName(), payloadType));
            }
        }
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

    @SneakyThrows
    protected static String[] getRequiredRoles(Class<?> payloadClass) {
        for (Annotation annotation : payloadClass.getAnnotations()) {
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
        }
        return null;
    }


}
