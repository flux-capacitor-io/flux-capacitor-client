package io.fluxcapacitor.javaclient.modeling;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandlerFactory;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentClock;
import static java.util.Optional.ofNullable;

@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
public class ImmutableAggregateRoot<T> implements AggregateRoot<T> {
    @JsonProperty
    ImmutableEntity<T> delegate;

    @JsonProperty
    String lastEventId;
    @JsonProperty
    @With
    Long lastEventIndex;
    @JsonProperty
    @Builder.Default
    Instant timestamp = FluxCapacitor.currentClock().instant();
    @JsonProperty
    @Builder.Default
    long sequenceNumber = -1L;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @With
    transient ImmutableAggregateRoot<T> previous;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    Set<Relationship> relationships = AggregateRoot.super.relationships();

    public static <T> ImmutableAggregateRoot<T> from(AggregateRoot<T> a,
                                                     EventSourcingHandlerFactory handlerFactory,
                                                     Serializer serializer) {
        if (a == null) {
            return null;
        }
        return ImmutableAggregateRoot.<T>builder()
                .delegate(ImmutableEntity.<T>builder().handlerFactory(handlerFactory).serializer(serializer)
                                  .id(a.id()).value(a.get()).type(a.type()).idProperty(a.idProperty()).build())
                .lastEventId(a.lastEventId())
                .lastEventIndex(a.lastEventIndex())
                .timestamp(a.timestamp())
                .sequenceNumber(a.sequenceNumber())
                .previous(ImmutableAggregateRoot.from(a.previous(), handlerFactory, serializer))
                .build();
    }

    @Override
    public ImmutableAggregateRoot<T> apply(Message message) {
        return apply(new DeserializingMessage(message.serialize(delegate.serializer()),
                                              type -> delegate.serializer().convert(message.getPayload(), type),
                                              EVENT));
    }

    public ImmutableAggregateRoot<T> apply(DeserializingMessage message) {
        return toBuilder()
                .delegate(delegate.apply(message))
                .previous(this)
                .timestamp(message.getTimestamp())
                .lastEventId(message.getMessageId())
                .lastEventIndex(message.getIndex())
                .sequenceNumber(sequenceNumber() + 1L)
                .build();
    }

    @Override
    public ImmutableAggregateRoot<T> update(UnaryOperator<T> function) {
        return toBuilder()
                .delegate(delegate.toBuilder().value(function.apply(get())).build())
                .previous(this)
                .timestamp(currentClock().instant())
                .build();
    }

    @Override
    public Object id() {
        return delegate().id();
    }

    @Override
    public Class<T> type() {
        return delegate().type();
    }

    @Override
    public T get() {
        return delegate.get();
    }

    @Override
    public String idProperty() {
        return delegate.idProperty();
    }

    @Override
    public Collection<? extends Entity<?, ?>> entities() {
        return delegate.entities();
    }

    public ImmutableAggregateRoot<T> withEventIndex(Long index, String messageId) {
        if (Objects.equals(messageId, lastEventId)) {
            return lastEventIndex == null ? withLastEventIndex(index) : this;
        }
        return withPrevious(previous.withEventIndex(index, messageId));
    }

    public Long highestEventIndex() {
        return ofNullable(lastEventIndex).or(
                () -> ofNullable(previous).map(ImmutableAggregateRoot::highestEventIndex)).orElse(null);
    }
}
