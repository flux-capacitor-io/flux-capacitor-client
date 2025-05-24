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

import io.fluxcapacitor.common.handling.TypedParameterResolver;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.NonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

/**
 * Resolves parameters of type {@link User}, typically representing the current user in a request context.
 * <p>
 * This resolver delegates the extraction of user information to a configured {@link UserProvider}, which defines how to
 * extract a user from a {@link HasMessage} or {@link DeserializingMessage}.
 * <p>
 * If no user can be determined from the message context, the {@link User#getCurrent()} thread-local fallback is used.
 */
public class UserParameterResolver extends TypedParameterResolver<Object> {
    private final UserProvider userProvider;

    public UserParameterResolver(@NonNull UserProvider userProvider) {
        super(User.class);
        this.userProvider = userProvider;
    }

    @Override
    public Function<Object, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return m -> (m instanceof HasMessage
                ? Optional.of(((HasMessage) m)) : ofNullable(DeserializingMessage.getCurrent()))
                .map(userProvider::fromMessage).orElseGet(User::getCurrent);
    }
}
