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
    public <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType, boolean onlyCached) {
        Optional<AggregateRepository> delegate = getDelegate(aggregateType);
        if (delegate.isPresent()) {
            return delegate.get().load(aggregateId, aggregateType, onlyCached);
        }
        throw new UnsupportedOperationException(
                "Could not a find a suitable aggregate repository for aggregate of type: " + aggregateType);
    }
    
    protected <T> Optional<AggregateRepository> getDelegate(Class<T> aggregateType) {
        return delegates.stream().filter(r -> r.supports(aggregateType)).findFirst();
    }
}
