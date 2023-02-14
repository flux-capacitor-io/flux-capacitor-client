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
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
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

    @NonNull MessageType messageType;
    @NonNull String name;
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
    @Singular
    List<HandlerInterceptor> handlerInterceptors;
    @Default
    @Accessors(fluent = true)
    boolean filterMessageTarget = false;
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

    public static Stream<ConsumerConfiguration> configurations(Collection<Class<?>> handlerClasses) {
        return Stream.concat(handlerClasses.stream().flatMap(ConsumerConfiguration::classConfigurations),
                             handlerClasses.stream().map(Class::getPackage).distinct().flatMap(
                                             p -> ReflectionUtils.getPackageAndParentPackages(p).stream()).distinct()
                                     .sorted(Comparator.comparing(Package::getName).reversed()).flatMap(
                                             ConsumerConfiguration::packageConfigurations));
    }

    private static Stream<ConsumerConfiguration> classConfigurations(Class<?> type) {
        return Optional.ofNullable(ReflectionUtils.getTypeAnnotation(type, Consumer.class))
                .map(c -> ReflectionUtils.getAllMethods(type).stream()
                        .flatMap(m -> ReflectionUtils.getAnnotation(m, HandleMessage.class).stream())
                        .map(HandleMessage::value).distinct().map(messageType -> getConfiguration(
                                c, h -> h.getClass().equals(type), messageType)))
                .orElseGet(Stream::empty);
    }

    private static Stream<ConsumerConfiguration> packageConfigurations(Package p) {
        return ReflectionUtils.getPackageAnnotation(p, Consumer.class)
                .map(c -> Arrays.stream(MessageType.values()).map(messageType -> getConfiguration(
                        c, h -> h.getClass().getPackage().equals(p)
                                || h.getClass().getPackage().getName().startsWith(p.getName() + "."),
                        messageType)))
                .orElseGet(Stream::empty);
    }

    private static ConsumerConfiguration getConfiguration(
            Consumer consumer, Predicate<Object> handlerFilter, MessageType messageType) {
        return ConsumerConfiguration.builder()
                .name(consumer.name())
                .handlerFilter(handlerFilter)
                .messageType(messageType)
                .errorHandler(asInstance(consumer.errorHandler()))
                .threads(consumer.threads())
                .maxFetchSize(consumer.maxFetchSize())
                .maxWaitDuration(Duration.of(consumer.maxWaitDuration(), consumer.durationUnit()))
                .batchInterceptors(Arrays.stream(consumer.batchInterceptors()).map(
                        ReflectionUtils::<BatchInterceptor>asInstance).collect(Collectors.toList()))
                .filterMessageTarget(consumer.filterMessageTarget())
                .ignoreSegment(consumer.ignoreSegment())
                .singleTracker(consumer.singleTracker())
                .minIndex(consumer.minIndex() < 0 ? null : consumer.minIndex())
                .maxIndexExclusive(consumer.maxIndexExclusive() < 0 ? null : consumer.maxIndexExclusive())
                .exclusive(consumer.exclusive())
                .passive(consumer.passive())
                .typeFilter(consumer.typeFilter().isBlank() ? null : consumer.typeFilter())
                .build();
    }
}
