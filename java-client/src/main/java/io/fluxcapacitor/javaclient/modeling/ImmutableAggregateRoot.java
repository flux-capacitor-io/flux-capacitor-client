package io.fluxcapacitor.javaclient.modeling;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandler;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentClock;
import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static java.util.Optional.ofNullable;

@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
public class ImmutableAggregateRoot<T> implements AggregateRoot<T> {
    @JsonProperty
    String id;
    @JsonProperty
    Class<T> type;
    @JsonProperty
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type")
    T value;
    @JsonProperty
    String lastEventId;
    @JsonProperty
    @With
    Long lastEventIndex;
    @JsonProperty
    @Builder.Default Instant timestamp = FluxCapacitor.currentClock().instant();
    @JsonProperty
    @Builder.Default long sequenceNumber = -1L;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @With
    transient ImmutableAggregateRoot<T> previous;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient EventSourcingHandler<T> eventSourcingHandler;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Serializer serializer;

    public static <T> ImmutableAggregateRoot<T> from(AggregateRoot<T> a,
                                                     EventSourcingHandler<T> eventSourcingHandler,
                                                     Serializer serializer) {
        if (a == null) {
            return null;
        }
        return ImmutableAggregateRoot.<T>builder()
                .id(a.id())
                .type(a.type())
                .value(a.get())
                .lastEventId(a.lastEventId())
                .lastEventIndex(a.lastEventIndex())
                .timestamp(a.timestamp())
                .sequenceNumber(a.sequenceNumber())
                .serializer(serializer)
                .eventSourcingHandler(eventSourcingHandler)
                .previous(ImmutableAggregateRoot.from(a.previous(), eventSourcingHandler, serializer))
                .build();
    }

    @Override
    public ImmutableAggregateRoot<T> apply(Message message) {
        return apply(new DeserializingMessage(message.serialize(serializer),
                                              type -> serializer.convert(message.getPayload(), type), EVENT));
    }

    public ImmutableAggregateRoot<T> apply(DeserializingMessage message) {
        return toBuilder()
                .value(eventSourcingHandler.invoke(this, message))
                .previous(this)
                .timestamp(message.getTimestamp())
                .lastEventId(message.getMessageId())
                .lastEventIndex(message.getIndex())
                .sequenceNumber(sequenceNumber() + 1L)
                .build();
    }

    @Override
    public AggregateRoot<T> apply(Object event) {
        if (event instanceof DeserializingMessage) {
            return apply(((DeserializingMessage) event));
        }
        return apply(asMessage(event));
    }

    @Override
    public ImmutableAggregateRoot<T> update(UnaryOperator<T> function) {
        return toBuilder()
                .value(function.apply(get()))
                .previous(this)
                .timestamp(currentClock().instant())
                .build();
    }

    @Override
    public <E extends Exception> ImmutableAggregateRoot<T> assertLegal(Object... commands) throws E {
        switch (commands.length) {
            case 0:
                return this;
            case 1:
                ValidationUtils.assertLegal(commands[0], this);
                return this;
            default:
                ImmutableAggregateRoot<T> result = this;
                Iterator<Object> iterator = Arrays.stream(commands).iterator();
                while (iterator.hasNext()) {
                    Object c = iterator.next();
                    ValidationUtils.assertLegal(c, result);
                    if (iterator.hasNext()) {
                        result = result.apply(Message.asMessage(c));
                    }
                }
                return this;
        }
    }

    @Override
    public T get() {
        return value;
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
