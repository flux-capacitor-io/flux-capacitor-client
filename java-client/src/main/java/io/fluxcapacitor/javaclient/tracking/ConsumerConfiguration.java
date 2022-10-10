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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.tracking.handling.HandleMessage;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.asInstance;

@Value
@Builder(builderClassName = "Builder", toBuilder = true)
public class ConsumerConfiguration {
    public static Function<MessageType, String> DEFAULT_CONSUMER_NAME = Enum::name;

    @NonNull MessageType messageType;
    @NonNull String name;
    @Default
    @Accessors(fluent = true)
    boolean prependApplicationName = true;
    @NonNull
    @Default
    @EqualsAndHashCode.Exclude
    Predicate<Object> handlerFilter = o -> true;
    @NonNull
    @Default
    @EqualsAndHashCode.Exclude
    ErrorHandler errorHandler = new LoggingErrorHandler();

    //tracking config
    @Default
    int threads = 1;
    @Default
    String typeFilter = null;
    @Default
    int maxFetchSize = 1024;
    @Default
    @NonNull Duration maxWaitDuration = Duration.ofSeconds(60);
    @Singular
    List<BatchInterceptor> batchInterceptors;
    @Default
    @Accessors(fluent = true)
    boolean ignoreMessageTarget = false;
    @Default
    @Accessors(fluent = true)
    boolean ignoreSegment = false;
    @Default
    @Accessors(fluent = true)
    boolean singleTracker = false;
    @Default
    Long minIndex = null;
    @Default
    Long maxIndexExclusive = null;
    @Default
    @Accessors(fluent = true)
    boolean exclusive = true;
    @Default
    @Accessors(fluent = true)
    boolean passive = false;
    @Default
    @EqualsAndHashCode.Exclude
    Function<Client, String> trackerIdFactory = client -> String.format("%s_%s", client.id(), UUID.randomUUID());
    @Default
    Duration purgeDelay = null;

    public static ConsumerConfiguration getDefault(MessageType messageType) {
        return ConsumerConfiguration.builder().messageType(messageType)
                .name(DEFAULT_CONSUMER_NAME.apply(messageType))
                .ignoreSegment(EnumSet.of(MessageType.NOTIFICATION, MessageType.RESULT).contains(messageType))
                .build();
    }

    public static Stream<ConsumerConfiguration> handlerConfigurations(@NonNull Class<?> handlerClass) {
        return Optional.ofNullable(ReflectionUtils.getTypeAnnotation(handlerClass, Consumer.class))
                .map(c -> ReflectionUtils.getAllMethods(handlerClass).stream()
                        .flatMap(m -> ReflectionUtils.getAnnotation(m, HandleMessage.class).stream())
                        .map(HandleMessage::value).distinct()
                        .map(messageType -> ConsumerConfiguration.builder()
                                .messageType(messageType)

                                .name(c.name().isBlank() ? handlerClass.getName() : c.name())
                                .prependApplicationName(false)
                                .handlerFilter(o -> handlerClass.equals(o.getClass()))
                                .errorHandler(!c.customErrorHandler().equals(ErrorHandler.class)
                                                      ? asInstance(c.customErrorHandler())
                                                      : c.retryOnError() ? new RetryingErrorHandler(c.stopAfterError())
                                        : c.stopAfterError() ? new ThrowingErrorHandler() : new LoggingErrorHandler())
                                .threads(c.threads())
                                .maxFetchSize(c.maxFetchSize())
                                .maxWaitDuration(Duration.of(c.maxWaitDuration(), c.durationUnit()))
                                .batchInterceptors(Arrays.stream(c.batchInterceptors()).map(
                                        ReflectionUtils::<BatchInterceptor>asInstance).collect(Collectors.toList()))
                                .ignoreMessageTarget(c.ignoreMessageTarget())
                                .ignoreSegment(c.ignoreSegment())
                                .singleTracker(c.singleTracker())
                                .minIndex(c.minIndex() < 0 ? null : c.minIndex())
                                .maxIndexExclusive(c.maxIndexExclusive() < 0 ? null : c.maxIndexExclusive())
                                .exclusive(c.exclusive())
                                .passive(c.passive())
                                .typeFilter(c.typeFilter().isBlank() ? null : c.typeFilter())

                                .build()))
                .orElseGet(Stream::empty);
    }
}
