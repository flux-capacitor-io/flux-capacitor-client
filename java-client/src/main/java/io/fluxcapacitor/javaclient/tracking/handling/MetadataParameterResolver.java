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

import io.fluxcapacitor.common.api.HasMetadata;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.handling.TypedParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

/**
 * Resolves handler method parameters of type {@link Metadata}.
 * <p>
 * This resolver can inject metadata into handler methods in two ways:
 * <ul>
 *   <li>If the message being handled implements {@link HasMetadata}, its metadata is directly returned.</li>
 *   <li>Otherwise, the metadata is extracted from the current {@link DeserializingMessage} (if available).</li>
 * </ul>
 *
 * <p>Example handler:
 * <pre>{@code
 * @HandleCommand
 * public void handle(MyCommand command, Metadata metadata) {
 *     String user = metadata.get("userId");
 * }
 * }</pre>
 */
public class MetadataParameterResolver extends TypedParameterResolver<Object> {

    public MetadataParameterResolver() {
        super(Metadata.class);
    }

    @Override
    public Function<Object, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return m -> m instanceof HasMetadata hasMetadata ? hasMetadata.getMetadata()
                : ofNullable(DeserializingMessage.getCurrent()).map(DeserializingMessage::getMetadata).orElse(null);
    }
}
