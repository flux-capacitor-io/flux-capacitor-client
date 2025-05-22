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

/**
 * Configuration class that defines how a message consumer behaves during message tracking and handler invocation.
 * <p>
 * {@code ConsumerConfiguration} is used to fine-tune the behavior of message consumers beyond what is possible with the
 * {@link Consumer} annotation. It supports handler filtering, tracking concurrency, custom interceptors, and more.
 *
 * <p><strong>Usage:</strong> Consumers can be declared programmatically using this configuration object, or generated
 * automatically from {@code @Consumer} annotations on handler classes or packages.
 *
 * @see Consumer
 * @see io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder#addConsumerConfiguration
 */
@Value
@Builder(builderClassName = "Builder", toBuilder = true)
public class ConsumerConfiguration {

    /**
     * Unique name for the consumer. Used for tracking and identifying its state.
     */
    @NonNull
    String name;

    /**
     * Optional predicate that determines whether a given handler is included in this consumer.
     * <p>
     * This is especially useful when combining multiple handler classes or packages and you want to restrict
     * consumption to a subset.
     */
    @NonNull
    @Default
    @EqualsAndHashCode.Exclude
    Predicate<Object> handlerFilter = o -> true;

    /**
     * Defines how errors during handler invocation are handled. Defaults to logging errors and continuing with other
     * messages.
     */
    @NonNull
    @Default
    @EqualsAndHashCode.Exclude
    ErrorHandler errorHandler = new LoggingErrorHandler();

    //tracking config

    /**
     * Number of concurrent threads to process messages.
     */
    @Default
    int threads = 1;

    /**
     * Optional class name pattern to filter message types. When provided, only messages whose payload matches the type
     * name pattern will be processed.
     */
    @Default
    String typeFilter = null;

    /**
     * Maximum number of messages to fetch per poll from the message log.
     */
    @Default
    int maxFetchSize = 1024;

    /**
     * Maximum wait time for polling new messages.
     */
    @Default
    @NonNull
    Duration maxWaitDuration = Duration.ofSeconds(60);

    /**
     * Interceptors that are invoked before and after a batch of messages is processed.
     */
    @Singular
    List<BatchInterceptor> batchInterceptors;

    /**
     * Interceptors that wrap handler execution for additional behavior (e.g. logging, metrics, authorization).
     */
    @Singular
    List<HandlerInterceptor> handlerInterceptors;

    /**
     * If true, only messages that target the current application will be processed. This allows isolating handlers to
     * specific applications.
     */
    @Default
    @Accessors(fluent = true)
    boolean filterMessageTarget = false;

    /**
     * If true, the consumer will ignore the segment assigned to it and process all messages regardless of partitioning.
     * Useful for global handlers such as notifications or singletons.
     */
    @Default
    @Accessors(fluent = true)
    boolean ignoreSegment = false;

    /**
     * If true, forces a single tracker to be created for this consumer regardless of segment count.
     */
    @Default
    @Accessors(fluent = true)
    boolean singleTracker = false;

    /**
     * If true, the consumer's position in the log will be controlled by the client (not automatically). Allows handling
     * messages manually or out of order.
     */
    @Default
    @Accessors(fluent = true)
    boolean clientControlledIndex = false;

    /**
     * Optional minimum index to start processing messages from.
     */
    @Default
    Long minIndex = null;

    /**
     * Optional exclusive upper limit for message index (messages with this index or higher will be skipped).
     */
    @Default
    Long maxIndexExclusive = null;

    /**
     * If true (default), ensures this consumer is exclusive — no other consumers with the same name will run
     * concurrently.
     */
    @Default
    @Accessors(fluent = true)
    boolean exclusive = true;

    /**
     * If true, the consumer will passively listen to messages without marking them as consumed. Useful for debug,
     * logging, or metrics purposes.
     */
    @Default
    @Accessors(fluent = true)
    boolean passive = false;

    /**
     * Factory for generating a tracker ID for this consumer. The default implementation appends a random UUID to the
     * client ID.
     */
    @Default
    @EqualsAndHashCode.Exclude
    Function<Client, String> trackerIdFactory = client -> String.format("%s_%s", client.id(), UUID.randomUUID());

    /**
     * Optional delay after which previously consumed segments should be purged from memory.
     */
    @Default
    Duration purgeDelay = null;

    /**
     * Regulates the flow of messages to the consumer to prevent overload. Defaults to {@link NoOpFlowRegulator}, which
     * performs no throttling.
     */
    @Default
    FlowRegulator flowRegulator = NoOpFlowRegulator.getInstance();

    /* ---------- Utilities for deriving configuration from annotations ---------- */

    /**
     * Returns a stream of {@code ConsumerConfiguration}s by inspecting the given handler classes and their packages.
     * Includes both class-level and package-level {@code @Consumer} annotations.
     */
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
