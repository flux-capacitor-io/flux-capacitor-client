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

package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.common.Registration;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public interface Cache extends AutoCloseable {
    Object put(Object id, Object value);

    Object putIfAbsent(Object id, Object value);

    <T> T computeIfAbsent(Object id, Function<? super Object, T> mappingFunction);

    <T> T computeIfPresent(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction);

    <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction);

    <T> T get(Object id);

    default <T> T getOrDefault(Object id, T defaultValue) {
        return Optional.<T>ofNullable(get(id)).orElse(defaultValue);
    }

    boolean containsKey(Object id);

    <T> T remove(Object id);

    void clear();

    int size();

    default boolean isEmpty() {
        return size() < 1;
    }

    Registration registerEvictionListener(Consumer<CacheEvictionEvent> listener);

    @Override
    void close();

}
