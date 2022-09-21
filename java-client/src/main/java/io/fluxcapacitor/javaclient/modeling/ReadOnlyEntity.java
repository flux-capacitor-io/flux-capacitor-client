package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.Message;
import lombok.Value;

import java.util.function.UnaryOperator;

@Value
public class ReadOnlyEntity<T> extends DelegatingEntity<T> {
    public ReadOnlyEntity(Entity<T> delegate) {
        super(delegate);
    }

    @Override
    public Entity<T> apply(Message eventMessage) {
        throw new UnsupportedOperationException("This aggregate is read-only");
    }

    @Override
    public Entity<T> update(UnaryOperator<T> function) {
        throw new UnsupportedOperationException("This aggregate is read-only");
    }
}
