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

import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;

public class DefaultMemoizingSupplier<T> implements MemoizingSupplier<T> {
    private static final Object singleton = new Object();
    private final MemoizingFunction<Object, T> delegate;

    public DefaultMemoizingSupplier(Supplier<T> delegate) {
        this(delegate, null, null);
    }

    public DefaultMemoizingSupplier(Supplier<T> delegate, Duration lifespan, Supplier<Clock> clockSupplier) {
        this.delegate = new DefaultMemoizingFunction<>(o -> delegate.get(), lifespan, clockSupplier);
    }

    @Override
    public T get() {
        return delegate.apply(singleton);
    }

    @Override
    public boolean isCached() {
        return delegate.isCached(singleton);
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
