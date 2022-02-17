/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.persisting.caching;

import lombok.NonNull;

import java.util.function.BiFunction;
import java.util.function.Function;

public enum NoOpCache implements Cache {
    INSTANCE;

    @Override
    public void put(Object id, @NonNull Object value) {
        //no op
    }

    @Override
    public void putIfAbsent(Object id, @NonNull Object value) {
        //no op
    }

    @Override
    public <T> T computeIfAbsent(Object id, Function<? super Object, T> mappingFunction) {
        return mappingFunction.apply(id);
    }

    @Override
    public <T> T computeIfPresent(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        return null;
    }

    @Override
    public <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        return mappingFunction.apply(id, null);
    }

    @Override
    public <T> T getIfPresent(Object id) {
        return null;
    }

    @Override
    public void invalidate(Object id) {
        //no op
    }

    @Override
    public void invalidateAll() {
        //no op
    }
}
