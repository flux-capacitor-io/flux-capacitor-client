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

import io.fluxcapacitor.common.MemoizingBiFunction;
import io.fluxcapacitor.common.MemoizingFunction;
import io.fluxcapacitor.common.MemoizingSupplier;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.ThrowingRunnable;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.modeling.SearchParameters;
import io.fluxcapacitor.javaclient.modeling.Searchable;
import io.fluxcapacitor.javaclient.tracking.TrackSelf;
import io.fluxcapacitor.javaclient.tracking.handling.LocalHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.lang.reflect.Executable;
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
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotationAs;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPackageAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotation;
import static java.time.temporal.ChronoUnit.DAYS;

@Slf4j
public class ClientUtils {
    public static final Marker ignoreMarker = MarkerFactory.getMarker("ignoreError");
    private static final BiFunction<Class<?>, java.lang.reflect.Executable, Optional<LocalHandler>> localHandlerCache =
            memoize((target, method) -> getAnnotation(method, LocalHandler.class)
                    .or(() -> Optional.ofNullable(getTypeAnnotation(target, LocalHandler.class)))
                    .or(() -> getPackageAnnotation(target.getPackage(), LocalHandler.class)));

    private static final BiFunction<Class<?>, java.lang.reflect.Executable, Optional<TrackSelf>> trackSelfCache =
            memoize((target, method) -> getAnnotation(method, TrackSelf.class)
                    .or(() -> Optional.ofNullable(getTypeAnnotation(target, TrackSelf.class)))
                    .or(() -> getPackageAnnotation(target.getPackage(), TrackSelf.class)));

    private static final Function<Class<?>, SearchParameters> searchParametersCache =
            memoize(type -> getAnnotationAs(type, Searchable.class, SearchParameters.class)
                    .map(p -> p.getCollection() == null ? p.withCollection(type.getSimpleName()) : p)
                    .orElseGet(() -> new SearchParameters(true, type.getSimpleName(), null, null)));

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

    public static void tryRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("Task {} failed", task, e);
        }
    }

    @SneakyThrows
    public static void runSilently(ThrowingRunnable runnable) {
        runnable.run();
    }

    public static boolean isSelfTracking(Class<?> target, Executable method) {
        return trackSelfCache.apply(target, method).isPresent();
    }

    public static Optional<TrackSelf> getTrackSelfAnnotation(Class<?> target, java.lang.reflect.Executable method) {
        return trackSelfCache.apply(target, method);
    }

    public static Optional<LocalHandler> getLocalHandlerAnnotation(Class<?> target,
                                                                   java.lang.reflect.Executable method) {
        return localHandlerCache.apply(target, method);
    }

    public static boolean isLocalHandler(Class<?> target, java.lang.reflect.Executable method) {
        return getLocalHandlerAnnotation(target, method).map(LocalHandler::value).orElse(false);
    }

    public static boolean isLocalHandler(HandlerInvoker invoker) {
        return invoker.getMethod() != null && isLocalHandler(invoker.getTargetClass(), invoker.getMethod());
    }

    public static boolean isTrackingHandler(Class<?> target, java.lang.reflect.Executable method) {
        return getLocalHandlerAnnotation(target, method).map(l -> !l.value() || l.allowExternalMessages())
                .orElse(true);
    }

    public static <T> MemoizingSupplier<T> memoize(Supplier<T> supplier) {
        return ObjectUtils.memoize(supplier);
    }

    public static <K, V> MemoizingFunction<K, V> memoize(Function<K, V> supplier) {
        return ObjectUtils.memoize(supplier);
    }

    public static <T, U, R> MemoizingBiFunction<T, U, R> memoize(BiFunction<T, U, R> supplier) {
        return ObjectUtils.memoize(supplier);
    }

    public static <T> MemoizingSupplier<T> memoize(Supplier<T> supplier, Duration lifespan) {
        return new MemoizingSupplier<>(supplier, lifespan, FluxCapacitor::currentClock);
    }

    public static <K, V> MemoizingFunction<K, V> memoize(Function<K, V> supplier, Duration lifespan) {
        return new MemoizingFunction<>(supplier, lifespan, FluxCapacitor::currentClock);
    }

    public static <T, U, R> MemoizingBiFunction<T, U, R> memoize(BiFunction<T, U, R> supplier,
                                                                 Duration lifespan) {
        return new MemoizingBiFunction<>(supplier, lifespan, FluxCapacitor::currentClock);
    }

    public static SearchParameters getSearchParameters(Class<?> type) {
        return searchParametersCache.apply(type);
    }

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
