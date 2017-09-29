/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.common.repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class InMemoryRepository<T> implements Repository<T> {

    private final Map<Object, T> values;

    public InMemoryRepository() {
        this(new ConcurrentHashMap<>());
    }

    protected InMemoryRepository(Map<Object, T> map) {
        values = map;
    }

    @Override
    public void put(Object id, T value) {
        values.put(id, value);
    }

    @Override
    public T get(Object id) {
        return values.get(id);
    }

    @Override
    public void delete(Object id) {
        values.remove(id);
    }

    protected Map<Object, T> getValues() {
        return values;
    }

    public Stream<T> find(Predicate<? super T> predicate) {
        return values.values().stream().filter(predicate);
    }
}
