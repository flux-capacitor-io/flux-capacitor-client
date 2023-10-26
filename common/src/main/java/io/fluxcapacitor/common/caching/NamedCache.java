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

package io.fluxcapacitor.common.caching;

import io.fluxcapacitor.common.Registration;
import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@AllArgsConstructor
public class NamedCache implements Cache {
    private final Cache delegate;
    private final UnaryOperator<Object> idFunction;

    @Override
    public Object put(Object id, @NonNull Object value) {
        return delegate.put(idFunction.apply(id), value);
    }

    @Override
    public Object putIfAbsent(Object id, @NonNull Object value) {
        return delegate.putIfAbsent(idFunction.apply(id), value);
    }

    @Override
    public <T> T computeIfAbsent(Object id, Function<? super Object, T> mappingFunction) {
        return delegate.computeIfAbsent(idFunction.apply(id), mappingFunction);
    }

    @Override
    public <T> T computeIfPresent(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        return delegate.computeIfPresent(idFunction.apply(id), mappingFunction);
    }

    @Override
    public <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        return delegate.compute(idFunction.apply(id), mappingFunction);
    }

    @Override
    public <T> T get(Object id) {
        return delegate.get(idFunction.apply(id));
    }

    @Override
    public boolean containsKey(Object id) {
        return delegate.containsKey(idFunction.apply(id));
    }

    @Override
    public <T> T remove(Object id) {
        return delegate.remove(idFunction.apply(id));
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public Registration registerEvictionListener(Consumer<CacheEvictionEvent> listener) {
        return delegate.registerEvictionListener(listener);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
