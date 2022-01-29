package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.Message;
import lombok.Value;

import java.util.function.UnaryOperator;

@Value
public class ReadOnlyAggregateRoot<T> extends DelegatingAggregateRoot<T, AggregateRoot<T>> {
    public ReadOnlyAggregateRoot(AggregateRoot<T> delegate) {
        super(delegate);
    }

    @Override
    public AggregateRoot<T> apply(Message eventMessage) {
        throw new UnsupportedOperationException("This aggregate is read-only");
    }

    @Override
    public AggregateRoot<T> update(UnaryOperator<T> function) {
        throw new UnsupportedOperationException("This aggregate is read-only");
    }
}
