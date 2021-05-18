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

package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils.assertAuthorized;
import static io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils.isAuthorized;

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
                    m = m.withMetadata(userProvider.addToMetadata(m.getMetadata(), user));
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

    @Override
    public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler, String consumer) {
        return new AuthorizingHandler(HandlerInterceptor.super.wrap(handler, consumer));
    }

    @AllArgsConstructor
    private class AuthorizingHandler implements Handler<DeserializingMessage> {
        @Delegate(excludes = ExcludedMethods.class)
        private final Handler<DeserializingMessage> delegate;

        @Override
        public boolean canHandle(DeserializingMessage m) {
            if (!delegate.canHandle(m)) {
                return false;
            }
            return isAuthorized(delegate.getTarget().getClass(),
                                delegate.getMethod(m), userProvider.fromMetadata(m.getMetadata()));
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    private interface ExcludedMethods {
        boolean canHandle(DeserializingMessage message);
    }
}
