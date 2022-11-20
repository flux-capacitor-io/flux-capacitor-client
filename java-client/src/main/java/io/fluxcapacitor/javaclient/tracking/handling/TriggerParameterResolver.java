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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.publishing.correlation.DefaultCorrelationDataProvider;
import lombok.AllArgsConstructor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

@AllArgsConstructor
public class TriggerParameterResolver implements ParameterResolver<HasMessage> {
    private final Client client;
    private final Serializer serializer;
    private final DefaultCorrelationDataProvider correlationDataProvider = DefaultCorrelationDataProvider.INSTANCE;

    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, HasMessage value, Object target) {
        return ReflectionUtils.has(Trigger.class, parameter);
    }

    @Override
    public boolean filterMessage(HasMessage message, Parameter parameter) {
        Trigger trigger = parameter.getAnnotation(Trigger.class);
        if (trigger == null) {
            return false;
        }
        if (trigger.messageType().length > 0 && getTriggerMessageType(message)
                .filter(type -> Arrays.stream(trigger.messageType()).anyMatch(t -> t == type)).isEmpty()) {
            return false;
        }
        if (trigger.consumer().length > 0 && getConsumer(message)
                .filter(type -> Arrays.asList(trigger.consumer()).contains(type)).isEmpty()) {
            return false;
        }
        return getTriggerClass(message).filter(triggerClass -> {
            var parameterType = HasMessage.class.isAssignableFrom(parameter.getType())
                    ? Object.class : parameter.getType();
            var allowedTypes = trigger.value();
            return parameterType.isAssignableFrom(triggerClass) &&
                   (allowedTypes.length == 0
                    || Arrays.stream(allowedTypes).anyMatch(a -> a.isAssignableFrom(triggerClass)));
        }).isPresent();
    }

    @Override
    public Function<HasMessage, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return m -> ofNullable(m.getMetadata().get(correlationDataProvider.getCorrelationIdKey()))
                .flatMap(s -> {
                    try {
                        return Optional.of(Long.valueOf(s));
                    } catch (Exception ignored) {
                        return Optional.empty();
                    }
                }).flatMap(index -> getTriggerMessage(index, getTriggerClass(m).orElseThrow(),
                                                      getTriggerMessageType(m).orElseThrow())).
                        <Object>map(triggerMessage -> {
                    var parameterType = p.getType();
                    if (DeserializingMessage.class.isAssignableFrom(parameterType)) {
                        return triggerMessage;
                    }
                    if (HasMessage.class.isAssignableFrom(parameterType)) {
                        return triggerMessage.toMessage();
                    }
                    return triggerMessage.getPayload();
                }).orElse(null);
    }

    protected Optional<Class<?>> getTriggerClass(HasMessage message) {
        return ofNullable(message.getMetadata().get(correlationDataProvider.getTriggerKey()))
                .flatMap(s -> {
                    try {
                        return Optional.of(ReflectionUtils.classForName(s));
                    } catch (Exception ignored) {
                        return Optional.empty();
                    }
                });
    }

    protected Optional<MessageType> getTriggerMessageType(HasMessage message) {
        return ofNullable(message.getMetadata().get(correlationDataProvider.getTriggerTypeKey()))
                .flatMap(s -> {
                    try {
                        return Optional.of(MessageType.valueOf(s));
                    } catch (Exception ignored) {
                        return Optional.empty();
                    }
                });
    }

    protected Optional<String> getConsumer(HasMessage message) {
        return ofNullable(message.getMetadata().get(correlationDataProvider.getConsumerKey()));
    }

    protected Optional<DeserializingMessage> getTriggerMessage(long index, Class<?> type, MessageType messageType) {
        return client.getTrackingClient(messageType).readFromIndex(index, 1).stream()
                .flatMap(s -> serializer.deserializeMessages(Stream.of(s), messageType))
                .filter(d -> type.isAssignableFrom(d.getPayloadClass()))
                .findFirst();
    }
}