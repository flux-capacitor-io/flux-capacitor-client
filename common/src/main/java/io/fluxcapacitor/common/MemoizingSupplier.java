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

import java.util.function.Supplier;

/**
 * A {@link Supplier} that memoizes (caches) the result of a single computation.
 * The first time the supplier is called, the result is stored and reused for all subsequent calls until cleared.
 *
 * <p>Instances of this interface are typically created using:
 * {@code io.fluxcapacitor.javaclient.common.ClientUtils#memoize(Supplier)}
 *
 * <pre>{@code
 * MemoizingSupplier<Config> configSupplier = ClientUtils.memoize(this::loadConfig);
 * Config config = configSupplier.get(); // computes and caches
 * boolean cached = configSupplier.isCached(); // true
 * configSupplier.clear(); // clears the cached value
 * }</pre>
 *
 * <p>Time-bound memoization is also supported:
 * <pre>{@code
 * MemoizingSupplier<Token> tokenSupplier =
 *     ClientUtils.memoize(this::fetchToken, Duration.ofMinutes(15));
 * }</pre>
 *
 * @param <T> the result type
 */
public interface MemoizingSupplier<T> extends Supplier<T> {

    /**
     * Returns whether this supplier has already cached a value.
     */
    boolean isCached();

    /**
     * Clears the cached value, forcing the next call to recompute.
     */
    void clear();
}
