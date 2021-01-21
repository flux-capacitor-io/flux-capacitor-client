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

package io.fluxcapacitor.javaclient.modeling;

import lombok.AllArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
public class CompositeAggregateRepository implements AggregateRepository {

    private final List<AggregateRepository> delegates;

    public CompositeAggregateRepository(AggregateRepository... delegates) {
        this(Arrays.asList(delegates));
    }

    @Override
    public boolean supports(Class<?> aggregateType) {
        return delegates.stream().anyMatch(r -> r.supports(aggregateType));
    }

    @Override
    public boolean cachingAllowed(Class<?> aggregateType) {
        return getDelegate(aggregateType).map(d -> d.cachingAllowed(aggregateType))
                .orElseThrow(() -> new UnsupportedOperationException(
                "Could not a find a suitable aggregate repository for aggregate of type: " + aggregateType));
    }

    @Override
    public <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType, boolean readOnly, boolean onlyCached) {
        Optional<AggregateRepository> delegate = getDelegate(aggregateType);
        if (delegate.isPresent()) {
            return delegate.get().load(aggregateId, aggregateType, readOnly, onlyCached);
        }
        throw new UnsupportedOperationException(
                "Could not a find a suitable aggregate repository for aggregate of type: " + aggregateType);
    }

    protected <T> Optional<AggregateRepository> getDelegate(Class<T> aggregateType) {
        return delegates.stream().filter(r -> r.supports(aggregateType)).findFirst();
    }
}
