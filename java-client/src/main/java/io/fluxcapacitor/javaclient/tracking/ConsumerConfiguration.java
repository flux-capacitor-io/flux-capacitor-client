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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerFactory;
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
import static io.fluxcapacitor.common.reflection.ReflectionUtils.ifClass;

@Value
@Builder(builderClassName = "Builder", toBuilder = true)
public class ConsumerConfiguration {

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
    @Accessors(fluent = true)
    boolean clientControlledIndex = false;
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
    @Default
    FlowRegulator flowRegulator = NoOpFlowRegulator.getInstance();

    public static Stream<ConsumerConfiguration> configurations(Collection<Class<?>> handlerClasses) {
        return Stream.concat(handlerClasses.stream().flatMap(ConsumerConfiguration::classConfigurations),
                             handlerClasses.stream().map(Class::getPackage).distinct().flatMap(
                                             p -> ReflectionUtils.getPackageAndParentPackages(p).stream()).distinct()
                                     .sorted(Comparator.comparing(Package::getName).reversed()).flatMap(
                                             ConsumerConfiguration::packageConfigurations));
    }

    private static Stream<ConsumerConfiguration> classConfigurations(Class<?> type) {
        return Optional.ofNullable(ReflectionUtils.<Consumer>getTypeAnnotation(type, Consumer.class))
                .map(c -> getConfiguration(c, h -> HandlerFactory.getTargetClass(h).equals(type))).stream();
    }

    private static Stream<ConsumerConfiguration> packageConfigurations(Package p) {
        return ReflectionUtils.getPackageAnnotation(p, Consumer.class)
                .map(c -> getConfiguration(
                        c, h -> {
                            @SuppressWarnings("DataFlowIssue")
                            Class<?> type = ifClass(h) instanceof Class<?> t ? t : h.getClass();
                            return type.getPackage().equals(p)
                                   || type.getPackage().getName().startsWith(p.getName() + ".");
                        })).stream();
    }

    private static ConsumerConfiguration getConfiguration(Consumer consumer, Predicate<Object> handlerFilter) {
        return ConsumerConfiguration.builder()
                .name(consumer.name())
                .handlerFilter(handlerFilter)
                .errorHandler(asInstance(consumer.errorHandler()))
                .flowRegulator(asInstance(consumer.flowRegulator()))
                .threads(consumer.threads())
                .maxFetchSize(consumer.maxFetchSize())
                .maxWaitDuration(Duration.of(consumer.maxWaitDuration(), consumer.durationUnit()))
                .batchInterceptors(Arrays.stream(consumer.batchInterceptors()).map(
                        ReflectionUtils::<BatchInterceptor>asInstance).collect(Collectors.toList()))
                .handlerInterceptors(Arrays.stream(consumer.handlerInterceptors()).map(
                        ReflectionUtils::<HandlerInterceptor>asInstance).collect(Collectors.toList()))
                .filterMessageTarget(consumer.filterMessageTarget())
                .ignoreSegment(consumer.ignoreSegment())
                .clientControlledIndex(consumer.clientControlledIndex())
                .singleTracker(consumer.singleTracker())
                .minIndex(consumer.minIndex() < 0 ? null : consumer.minIndex())
                .maxIndexExclusive(consumer.maxIndexExclusive() < 0 ? null : consumer.maxIndexExclusive())
                .exclusive(consumer.exclusive())
                .passive(consumer.passive())
                .typeFilter(consumer.typeFilter().isBlank() ? null : consumer.typeFilter())
                .build();
    }
}
