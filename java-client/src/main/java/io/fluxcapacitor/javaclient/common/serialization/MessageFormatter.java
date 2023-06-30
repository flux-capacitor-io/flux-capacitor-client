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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;

import java.util.function.Function;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static java.lang.String.format;

@FunctionalInterface
public interface MessageFormatter extends Function<DeserializingMessage, String> {
    MessageFormatter DEFAULT = m -> m.isDeserialized() ? getAnnotatedPropertyValue(m.getPayload(), RoutingKey.class)
            .map(key -> format("%s (routing key: %s)", m.getPayloadClass().getSimpleName(), key))
            .orElseGet(() -> {
                try {
                    return m.getPayloadClass().getSimpleName();
                } catch (Exception e) {
                    return m.getType();
                }
            }) : m.getType();
}
