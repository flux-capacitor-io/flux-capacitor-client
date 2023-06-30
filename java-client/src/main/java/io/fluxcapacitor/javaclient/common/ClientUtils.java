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

import io.fluxcapacitor.common.ThrowingRunnable;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSelf;
import io.fluxcapacitor.javaclient.tracking.handling.LocalHandler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedMethods;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotation;

@Slf4j
public class ClientUtils {
    private static final BiFunction<Class<?>, java.lang.reflect.Executable, Optional<LocalHandler>> localHandlerCache = memoize(
            (target, method) -> getAnnotation(method, LocalHandler.class)
                    .or(() -> Optional.ofNullable(getTypeAnnotation(target, LocalHandler.class))));

    private static final Function<Class<?>, Optional<HandleSelf>> handleSelfCache = memoize(
            target -> getAnnotatedMethods(target, HandleSelf.class).stream()
                    .findFirst().map(m -> m.getAnnotation(HandleSelf.class)));

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

    public static boolean isLocalHandler(Class<?> target, java.lang.reflect.Executable method) {
        return getLocalHandlerAnnotation(target, method).map(LocalHandler::value).orElse(false);
    }

    public static boolean isLocalHandler(HandlerInvoker invoker) {
        return invoker.getTarget() != null && invoker.getMethod() != null && isLocalHandler(
                invoker.getTarget().getClass(), invoker.getMethod());
    }

    public static boolean isTrackingHandler(Class<?> target, java.lang.reflect.Executable method) {
        return getLocalHandlerAnnotation(target, method).map(l -> !l.value() || l.allowExternalMessages())
                .orElse(true);
    }

    public static Optional<LocalHandler> getLocalHandlerAnnotation(Class<?> target, java.lang.reflect.Executable method) {
        return localHandlerCache.apply(target, method);
    }

    public static Optional<HandleSelf> getHandleSelfAnnotation(Class<?> target) {
        return handleSelfCache.apply(target);
    }
}
