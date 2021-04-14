/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public class AggregateTypeResolver implements ParameterResolver<DeserializingMessage> {
    @Override
    public Function<DeserializingMessage, Object> resolve(Parameter p) {
        if (p.isAnnotationPresent(AggregateType.class)) {
            return AggregateTypeResolver::getAggregateType;
        }
        return null;
    }

    public static Class<?> getAggregateType(DeserializingMessage message) {
        return Optional.ofNullable(message.getMetadata().get(AggregateRoot.AGGREGATE_TYPE_METADATA_KEY)).map(c -> {
            try {
                return Class.forName(c);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }).orElse(null);
    }
}
