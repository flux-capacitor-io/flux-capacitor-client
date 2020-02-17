package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.String.format;

@AllArgsConstructor
public class AuthenticatingInterceptor implements DispatchInterceptor, HandlerInterceptor {

    private final UserSupplier userSupplier;

    @Override
    public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function,
                                                                  MessageType messageType) {
        return m -> {
            DeserializingMessage currentMessage = DeserializingMessage.getCurrent();
            User user = currentMessage == null ? userSupplier.get() 
                    : Optional.ofNullable((User) userSupplier.fromMetadata(m.getMetadata())).orElseGet(
                        userSupplier::getSystemUser);
            if (user == null) {
                userSupplier.removeFromMetadata(m.getMetadata());
            } else {
                userSupplier.addToMetadata(m.getMetadata(), user);
            }
            return function.apply(m);
        };
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    Handler<DeserializingMessage> handler,
                                                                    String consumer) {
        return m -> {
            User previous = User.getCurrent();
            User user = userSupplier.fromMetadata(m.getMetadata());
            try {
                User.current.set(user);
                String[] requiredRoles = getRequiredRoles(m.getPayloadClass());
                if (requiredRoles != null) {
                    if (user == null) {
                        throw new UnauthenticatedException(format("Message %s requires authentication", m.getType()));
                    }
                    if (Arrays.stream(requiredRoles).noneMatch(user::hasRole)) {
                        throw new UnauthorizedException(
                                format("User %s is unauthorized to issue %s", user.getName(), m.getType()));
                    }
                }
                return function.apply(m);
            } finally {
                User.current.set(previous);
            }
        };
    }

    @SneakyThrows
    protected String[] getRequiredRoles(Class<?> payloadClass) {
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
