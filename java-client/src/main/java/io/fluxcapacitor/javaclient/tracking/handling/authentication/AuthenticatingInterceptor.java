package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;

import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils.assertAuthorized;

@AllArgsConstructor
public class AuthenticatingInterceptor implements DispatchInterceptor, HandlerInterceptor {

    private final UserProvider userProvider;

    @Override
    public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function,
                                                                  MessageType messageType) {
        return m -> {
            if (!userProvider.containsUser(m.getMetadata())) {
                User user = userProvider.getActiveUser();
                if (user == null) {
                    user = Optional.ofNullable(DeserializingMessage.getCurrent())
                            .map(d -> userProvider.getSystemUser()).orElse(null);

                }
                if (user != null) {
                    userProvider.addToMetadata(m.getMetadata(), user);
                }
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
            User user = userProvider.fromMetadata(m.getMetadata());
            try {
                User.current.set(user);
                assertAuthorized(m.getPayloadClass(), user);
                return function.apply(m);
            } finally {
                User.current.set(previous);
            }
        };
    }
}
