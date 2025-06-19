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

package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.common.DefaultMemoizingBiFunction;
import io.fluxcapacitor.common.DefaultMemoizingFunction;
import io.fluxcapacitor.common.DefaultMemoizingSupplier;
import io.fluxcapacitor.common.MemoizingBiFunction;
import io.fluxcapacitor.common.MemoizingFunction;
import io.fluxcapacitor.common.MemoizingSupplier;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.modeling.SearchParameters;
import io.fluxcapacitor.javaclient.persisting.search.Searchable;
import io.fluxcapacitor.javaclient.tracking.TrackSelf;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCustom;
import io.fluxcapacitor.javaclient.tracking.handling.HandleDocument;
import io.fluxcapacitor.javaclient.tracking.handling.LocalHandler;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedMethods;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotationAs;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPackageAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotation;
import static java.time.temporal.ChronoUnit.DAYS;

/**
 * Utility class offering client-side support functions for working with Flux Capacitor.
 * <p>
 * Unlike {@link io.fluxcapacitor.common.ObjectUtils}, this class is specifically intended for use within Flux client
 * applications and can make use of infrastructure components such as
 * {@link io.fluxcapacitor.javaclient.FluxCapacitor}.
 * <p>
 * It provides convenience methods for:
 * <ul>
 *   <li>Memoization with lifespans via Flux’s clock</li>
 *   <li>Topic resolution for annotated handler methods</li>
 *   <li>Determining whether handlers are local or self-tracking</li>
 *   <li>Time-bound execution blocking</li>
 *   <li>Safe annotation parsing and revision introspection</li>
 *   <li>Truncating {@link java.time.temporal.Temporal} values</li>
 * </ul>
 */
@Slf4j
public class ClientUtils {
    /**
     * A marker used to denote specific log entries that should be ignored or treated differently, typically to bypass
     * error handling in logging frameworks.
     * <p>
     * This marker is created using the {@code MarkerFactory} with the identifier "ignoreError". It can be useful in
     * scenarios where certain log messages need to be flagged for exclusion from error-reporting workflows.
     */
    public static final Marker ignoreMarker = MarkerFactory.getMarker("ignoreError");

    private static final BiFunction<Class<?>, java.lang.reflect.Executable, Optional<LocalHandler>> localHandlerCache =
            memoize((target, method) -> getAnnotation(method, LocalHandler.class)
                    .or(() -> Optional.ofNullable(getTypeAnnotation(target, LocalHandler.class)))
                    .or(() -> getPackageAnnotation(target.getPackage(), LocalHandler.class))
                    .filter(LocalHandler::value));

    private static final BiFunction<Class<?>, java.lang.reflect.Executable, Optional<TrackSelf>> trackSelfCache =
            memoize((target, method) -> getAnnotation(method, TrackSelf.class)
                    .or(() -> Optional.ofNullable(getTypeAnnotation(target, TrackSelf.class)))
                    .or(() -> getPackageAnnotation(target.getPackage(), TrackSelf.class)));

    private static final Function<Class<?>, SearchParameters> searchParametersCache =
            memoize(type -> getAnnotationAs(type, Searchable.class, SearchParameters.class)
                    .map(p -> p.getCollection() == null ? p.withCollection(type.getSimpleName()) : p)
                    .orElseGet(() -> new SearchParameters(true, type.getSimpleName(), null, null)));

    /**
     * Blocks until all futures are complete or the maximum duration has elapsed.
     * <p>
     * Useful for tracking batched async operations (e.g., event publishing or indexing).
     *
     * @param maxDuration the maximum time to wait
     * @param futures     the set of futures to wait on
     */
    public static void waitForResults(Duration maxDuration, Collection<? extends Future<?>> futures) {
        Instant deadline = Instant.now().plus(maxDuration);
        for (Future<?> f : futures) {
            try {
                f.get(Math.max(0, Duration.between(Instant.now(), deadline).toMillis()), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Thread was interrupted before receiving all expected results", e);
                return;
            } catch (TimeoutException e) {
                log.warn("Timed out before having received all expected results", e);
                return;
            } catch (ExecutionException ignore) {
            }
        }
    }

    /**
     * Returns whether the specified method or its declaring class is marked with {@link TrackSelf}.
     *
     * @param target the handler class
     * @param method the method to inspect
     * @return {@code true} if marked for self-tracking, {@code false} otherwise
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isSelfTracking(Class<?> target, Executable method) {
        return trackSelfCache.apply(target, method).isPresent();
    }

    /**
     * Retrieves the {@link LocalHandler} annotation associated with a given handler, from its method, its declaring
     * class, or ancestral package, if present.
     */
    public static Optional<LocalHandler> getLocalHandlerAnnotation(HandlerInvoker handlerInvoker) {
        return localHandlerCache.apply(handlerInvoker.getTargetClass(), handlerInvoker.getMethod());
    }

    /**
     * Retrieves the {@link LocalHandler} annotation associated with a given method, its declaring class, or ancestral
     * package, if present.
     */
    public static Optional<LocalHandler> getLocalHandlerAnnotation(Class<?> target,
                                                                   java.lang.reflect.Executable method) {
        return localHandlerCache.apply(target, method);
    }

    /**
     * Determines if the specified handler method handles messages locally. A handler is considered a local if it is
     * explicitly annotated as such using {@link LocalHandler} or meets the criteria for local self-handling, i.e.:
     * {@link #isLocalSelfHandler} returns {@code true}.
     */
    public static boolean isLocalHandler(HandlerInvoker invoker, HasMessage message) {
        if (invoker.getMethod() == null) {
            return false;
        }
        return getLocalHandlerAnnotation(invoker.getTargetClass(), invoker.getMethod()).isPresent()
               || isLocalSelfHandler(invoker, message);
    }

    /**
     * Returns whether the handler method should only handle messages from the same instance ("self"). This is true when
     * the handler’s payload type equals the message type and no {@link TrackSelf} annotation is present.
     */
    public static boolean isLocalSelfHandler(HandlerInvoker invoker, HasMessage message) {
        return isSelfHandler(invoker, message)
               && !isSelfTracking(invoker.getTargetClass(), invoker.getMethod());
    }

    static boolean isSelfHandler(HandlerInvoker invoker, HasMessage message) {
        return Objects.equals(invoker.getTargetClass(), message.getPayloadClass());
    }

    @SuppressWarnings("SameParameterValue")
    static boolean isTrackingHandler(Class<?> target, java.lang.reflect.Executable method) {
        return getLocalHandlerAnnotation(target, method).map(LocalHandler::allowExternalMessages).orElse(true);
    }

    /**
     * Memoizes the given supplier using a default memoization strategy.
     */
    public static <T> MemoizingSupplier<T> memoize(Supplier<T> supplier) {
        return ObjectUtils.memoize(supplier);
    }

    /**
     * Memoizes the given function using a default memoization strategy.
     */
    public static <K, V> MemoizingFunction<K, V> memoize(Function<K, V> supplier) {
        return ObjectUtils.memoize(supplier);
    }

    /**
     * Memoizes the given bi-function using a default memoization strategy.
     */
    public static <T, U, R> MemoizingBiFunction<T, U, R> memoize(BiFunction<T, U, R> supplier) {
        return ObjectUtils.memoize(supplier);
    }

    /**
     * Memoizes the given supplier using a time-based lifespan and Flux’s internal clock.
     */
    public static <T> MemoizingSupplier<T> memoize(Supplier<T> supplier, Duration lifespan) {
        return new DefaultMemoizingSupplier<>(supplier, lifespan, FluxCapacitor.currentClock());
    }

    /**
     * Memoizes the given function using a time-based lifespan and Flux’s internal clock.
     */
    public static <K, V> MemoizingFunction<K, V> memoize(Function<K, V> supplier, Duration lifespan) {
        return new DefaultMemoizingFunction<>(supplier, lifespan, FluxCapacitor.currentClock());
    }

    /**
     * Memoizes the given bi-function using a time-based lifespan and Flux’s internal clock.
     */
    public static <T, U, R> MemoizingBiFunction<T, U, R> memoize(BiFunction<T, U, R> supplier,
                                                                 Duration lifespan) {
        return new DefaultMemoizingBiFunction<>(supplier, lifespan, FluxCapacitor.currentClock());
    }

    /**
     * Extracts the revision number from the {@link Revision} annotation on the given object’s class.
     *
     * @return the revision number, or 0 if not present
     */
    public static int getRevisionNumber(Object object) {
        return Optional.ofNullable(object).map(o -> o.getClass().getAnnotation(Revision.class))
                .map(Revision::value).orElse(0);
    }

    /**
     * Determines the collection name used for document indexing and search based on the annotated handler or document
     * type.
     */
    public static String determineSearchCollection(@NonNull Object c) {
        return ReflectionUtils.ifClass(c) instanceof Class<?> type
                ? getSearchParameters(type).getCollection() : c.toString();
    }

    /**
     * Returns the effective {@link SearchParameters} for the given type, using the {@link Searchable} annotation.
     * Defaults to a collection name matching the class’s simple name if not explicitly set.
     */
    public static SearchParameters getSearchParameters(Class<?> type) {
        return searchParametersCache.apply(type);
    }

    /**
     * Extracts all topics associated with the given handler object and message type.
     * <p>
     * The topics are derived based on the handler's annotations or inferred class properties. This method delegates the
     * operation to another overload which processes a collection of handler classes.
     *
     * @param messageType the type of the message (e.g., DOCUMENT or CUSTOM)
     * @param handler     the handler object used to determine topics
     * @return a set of topic names associated with the handler and message type
     */
    public static Set<String> getTopics(MessageType messageType, Object handler) {
        return getTopics(messageType, Collections.singleton(ReflectionUtils.asClass(handler)));
    }

    /**
     * Extracts all topics from {@link HandleDocument} or {@link HandleCustom} annotated methods for the given classes.
     *
     * @param messageType    the type of message (DOCUMENT or CUSTOM)
     * @param handlerClasses the classes to inspect
     * @return a set of topic names
     */
    public static Set<String> getTopics(MessageType messageType, Collection<Class<?>> handlerClasses) {
        return switch (messageType) {
            case DOCUMENT -> handlerClasses.stream()
                    .flatMap(handlerClass -> getAnnotatedMethods(handlerClass, HandleDocument.class).stream())
                    .flatMap(m -> ReflectionUtils.<HandleDocument>getMethodAnnotation(m, HandleDocument.class)
                            .map(a -> getTopic(a, m)).stream()).collect(Collectors.toSet());
            case CUSTOM -> handlerClasses.stream()
                    .flatMap(handlerClass -> getAnnotatedMethods(handlerClass, HandleCustom.class).stream()).map(m -> {
                        var handleDocument = ReflectionUtils.<HandleCustom>getMethodAnnotation(
                                m, HandleCustom.class).filter(h -> !h.disabled());
                        return handleDocument.map(HandleCustom::value).orElse(null);
                    }).filter(Objects::nonNull).collect(Collectors.toSet());
            default -> Collections.emptySet();
        };
    }

    /**
     * Extracts the topic associated with a given {@link HandleDocument} annotation or {@link Executable}. The topic is
     * determined based on the document collection name, the associated document class, or parameter type of the
     * executable.
     *
     * @param handleDocument the {@link HandleDocument} annotation containing metadata about the document handler. Can
     *                       specify the document collection or class.
     * @param executable     the {@link Executable} (e.g., method or constructor) used to infer the topic if the
     *                       annotation does not provide one. The parameter type of the executable may determine the
     *                       topic.
     * @return the topic as a String, or {@code null} if no valid topic is determined.
     */
    public static String getTopic(HandleDocument handleDocument, Executable executable) {
        return Optional.ofNullable(handleDocument)
                .filter(h -> !h.disabled())
                .flatMap(h -> Optional.ofNullable(h.value()).filter(s -> !s.isBlank())
                        .or(() -> Void.class.equals(h.documentClass()) ? Optional.empty() :
                                Optional.of(ClientUtils.determineSearchCollection(h.documentClass()))))
                .or(() -> Arrays.stream(executable.getParameters()).findFirst().map(Parameter::getType).map(
                        ClientUtils::determineSearchCollection))
                .filter(s -> !s.isBlank()).orElse(null);
    }

    /**
     * Truncates a {@link Temporal} (e.g., {@link LocalDateTime}) to the specified unit (e.g., {@code MONTHS} or
     * {@code YEARS}). Automatically adjusts the truncation unit for compound units like {@code MONTHS -> DAYS}.
     *
     * @param timestamp the temporal value to truncate
     * @param unit      the unit to truncate to
     * @return a truncated version of the original value
     */
    @SuppressWarnings("unchecked")
    public static <T extends Temporal> T truncate(T timestamp, TemporalUnit unit) {
        T result = unit instanceof ChronoUnit chronoUnit ?
                switch (chronoUnit) {
                    case YEARS -> (T) timestamp.with(TemporalAdjusters.firstDayOfYear());
                    case MONTHS -> (T) timestamp.with(TemporalAdjusters.firstDayOfMonth());
                    default -> timestamp;
                } : timestamp;
        TemporalUnit truncateUnit = unit instanceof ChronoUnit chronoUnit ?
                switch (chronoUnit) {
                    case YEARS, MONTHS -> DAYS;
                    default -> chronoUnit;
                } : unit;

        if (result instanceof LocalDate) {
            return result;
        }
        if (result instanceof LocalDateTime r) {
            return (T) r.truncatedTo(truncateUnit);
        }
        if (result instanceof ZonedDateTime r) {
            return (T) r.truncatedTo(truncateUnit);
        }
        if (result instanceof OffsetDateTime r) {
            return (T) r.truncatedTo(truncateUnit);
        }
        if (result instanceof Instant r) {
            return (T) r.truncatedTo(truncateUnit);
        }
        throw new UnsupportedOperationException("Unsupported temporal type: " + result.getClass());
    }
}
