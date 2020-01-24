package io.fluxcapacitor.javaclient.modeling;

public interface AggregateRepository {
    
    boolean supports(Class<?> aggregateType);
    
    default <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType) {
        return load(aggregateId, aggregateType, false);
    }

    <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType, boolean onlyCached);

}
