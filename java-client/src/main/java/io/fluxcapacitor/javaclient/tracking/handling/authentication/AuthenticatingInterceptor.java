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

package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.WEBREQUEST;
import static io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils.assertAuthorized;
import static java.util.Optional.ofNullable;

@AllArgsConstructor
public class AuthenticatingInterceptor implements DispatchInterceptor, HandlerInterceptor {

    private final UserProvider userProvider;

    @Override
    public Message interceptDispatch(Message m, MessageType messageType, String topic) {
        if (!userProvider.containsUser(m.getMetadata())) {
            Optional<DeserializingMessage> currentMessage = ofNullable(DeserializingMessage.getCurrent());
            User user = currentMessage.isPresent() ?
                    currentMessage.get().getMessageType() == WEBREQUEST ? userProvider.getActiveUser() :
                            userProvider.getSystemUser() :
                    ofNullable(userProvider.getActiveUser())
                            .orElseGet(() -> messageType == WEBREQUEST ? null : userProvider.getSystemUser());
            if (user != null) {
                m = m.withMetadata(userProvider.addToMetadata(m.getMetadata(), user));
            }
        }
        return m;
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    HandlerInvoker invoker) {
        return m -> {
            User previous = User.getCurrent();
            User user = userProvider.fromMessage(m);
            try {
                User.current.set(user);
                if (m.getType() != null) {
                    assertAuthorized(m.getPayloadClass(), user);
                }
                return function.apply(m);
            } finally {
                User.current.set(previous);
            }
        };
    }

    @Override
    public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
        return new AuthorizingHandler(HandlerInterceptor.super.wrap(handler));
    }

    @AllArgsConstructor
    private class AuthorizingHandler implements Handler<DeserializingMessage> {
        private final Handler<DeserializingMessage> delegate;

        @Override
        public Optional<HandlerInvoker> getInvoker(DeserializingMessage m) {
            return delegate.getInvoker(m).flatMap(
                    i -> {
                        if (Optional.ofNullable(m.getPayloadClass())
                                .map(c -> i.getTargetClass().isAssignableFrom(c)).orElse(false)) {
                            return Optional.of(i);
                        }
                        User user;
                        try {
                            user = userProvider.fromMessage(m);
                        } catch (Throwable ignored) {
                            user = null;
                        }
                        if (user == null) {
                            user = userProvider.getActiveUser();
                        }
                        try {
                            return assertAuthorized(i.getTargetClass(), i.getMethod(), user)
                                    ? Optional.of(i) : Optional.empty();
                        } catch (Throwable e) {
                            return Optional.of(new HandlerInvoker.DelegatingHandlerInvoker(i) {
                                @Override
                                public Object invoke(BiFunction<Object, Object, Object> resultCombiner) {
                                    throw e;
                                }
                            });
                        }
                    });
        }

        @Override
        public Class<?> getTargetClass() {
            return delegate.getTargetClass();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
