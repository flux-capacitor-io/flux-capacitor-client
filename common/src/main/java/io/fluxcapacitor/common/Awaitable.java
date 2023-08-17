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

package io.fluxcapacitor.common;

import lombok.SneakyThrows;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;

@FunctionalInterface
public interface Awaitable {

    Executor executor = Executors.newCachedThreadPool(newThreadFactory("Awaitable"));

    void await() throws Exception;

    @SneakyThrows
    default void awaitSilently() {
        await();
    }

    default CompletableFuture<Void> asCompletableFuture() {
        return CompletableFuture.runAsync(this::awaitSilently, executor);
    }

    default Awaitable join(Awaitable other) {
        return () -> {
            await();
            other.await();
        };
    }

    static Awaitable ready() {
        return () -> {};
    }

    static Awaitable failed(Exception e) {
        return () -> {
            throw e;
        };
    }

    static Awaitable fromFuture(Future<?> future, Duration timeout) {
        return () -> future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    static Awaitable fromFuture(Future<?> future) {
        return future::get;
    }

}
