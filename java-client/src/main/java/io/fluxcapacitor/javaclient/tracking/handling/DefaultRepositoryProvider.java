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

package io.fluxcapacitor.javaclient.tracking.handling;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

/**
 * A default implementation of the {@link RepositoryProvider} interface that uses a {@link ConcurrentHashMap} for each
 * repository.
 */
public class DefaultRepositoryProvider implements RepositoryProvider {
    private final Function<Class<?>, Map<Object, Object>> delegate = memoize(c -> new ConcurrentHashMap<>());

    @SuppressWarnings("unchecked")
    @Override
    public <T> Map<Object, T> getRepository(Class<T> repositoryClass) {
        return (Map<Object, T>) delegate.apply(repositoryClass);
    }
}
