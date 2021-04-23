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

package io.fluxcapacitor.common;

import lombok.SneakyThrows;

@FunctionalInterface
public interface Awaitable {

    void await() throws Exception;

    @SneakyThrows
    default void awaitSilently() {
        await();
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

}
