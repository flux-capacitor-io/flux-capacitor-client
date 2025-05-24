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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.handling.TypedParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.function.Function;

/**
 * Resolves handler method parameters of type {@link DeserializingMessage}.
 * <p>
 * This allows handler methods to access the deserialization context, including message metadata, payload, and raw
 * serialized content.
 * <p>
 * Useful when advanced information about the message is needed, such as the Flux-assigned message index.
 */
public class DeserializingMessageParameterResolver extends TypedParameterResolver<Object> {

    public DeserializingMessageParameterResolver() {
        super(DeserializingMessage.class);
    }

    @Override
    public Function<Object, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return m -> m instanceof DeserializingMessage ? m : DeserializingMessage.getCurrent();
    }
}