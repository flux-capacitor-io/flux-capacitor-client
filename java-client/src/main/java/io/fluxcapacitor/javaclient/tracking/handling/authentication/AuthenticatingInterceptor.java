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
import io.fluxcapacitor.javaclient.tracking.handling.HandleSelf;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.fluxcapacitor.common.MessageType.WEBREQUEST;
import static io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils.assertAuthorized;
import static io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils.isAuthorized;
import static java.util.Optional.ofNullable;

@AllArgsConstructor
public class AuthenticatingInterceptor implements DispatchInterceptor, HandlerInterceptor {

    private final UserProvider userProvider;

    @Override
    public Message interceptDispatch(Message m, MessageType messageType) {
        if (!userProvider.containsUser(m.getMetadata())) {
            Optional<DeserializingMessage> currentMessage = ofNullable(DeserializingMessage.getCurrent());
            User user = currentMessage.isPresent()
                    ? currentMessage.get().getMessageType() == WEBREQUEST ? null : userProvider.getSystemUser()
                    : ofNullable(userProvider.getActiveUser()).orElseGet(userProvider::getSystemUser);
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
        @Delegate(excludes = ExcludedMethods.class)
        private final Handler<DeserializingMessage> delegate;

        @Override
        public Optional<HandlerInvoker> getInvoker(DeserializingMessage m) {
            return delegate.getInvoker(m).filter(i -> i.getMethodAnnotation() instanceof HandleSelf || isAuthorized(
                            i.getTargetClass(), i.getMethod(), userProvider.fromMessage(m)));
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    private interface ExcludedMethods {
        Optional<Supplier<?>> findInvoker(DeserializingMessage message);
    }
}
