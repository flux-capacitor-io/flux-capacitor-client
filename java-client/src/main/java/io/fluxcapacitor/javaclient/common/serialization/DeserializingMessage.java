package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.DeserializingMessageParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.MessageParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.MetadataParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.PayloadParameterResolver;
import lombok.Value;
import lombok.experimental.Delegate;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.iterate;
import static java.time.Instant.ofEpochMilli;

@Value
public class DeserializingMessage {
    public static MessageFormatter messageFormatter = MessageFormatter.DEFAULT;
    public static List<ParameterResolver<? super DeserializingMessage>> defaultParameterResolvers =
            Arrays.asList(new PayloadParameterResolver(), new MetadataParameterResolver(),
                          new DeserializingMessageParameterResolver(), new MessageParameterResolver());
    private static final ThreadLocal<DeserializingMessage> current = new ThreadLocal<>();

    public static Stream<DeserializingMessage> convert(Stream<DeserializingObject<byte[], SerializedMessage>> input,
                                                       MessageType messageType) {
        Iterator<DeserializingObject<byte[], SerializedMessage>> iterator = input.iterator();
        if (iterator.hasNext()) {
            return iterate(new DeserializingMessage(iterator.next(), messageType, !iterator.hasNext()),
                           m -> new DeserializingMessage(iterator.next(), messageType, !iterator.hasNext()),
                           DeserializingMessage::isLastOfBatch);
        }
        return Stream.empty();
    }

    @Delegate
    DeserializingObject<byte[], SerializedMessage> delegate;
    MessageType messageType;
    boolean lastOfBatch;

    public Metadata getMetadata() {
        return delegate.getSerializedObject().getMetadata();
    }

    public Message toMessage() {
        return new Message(delegate.getPayload(), getMetadata(),
                           delegate.getSerializedObject().getMessageId(),
                           ofEpochMilli(delegate.getSerializedObject().getTimestamp()));
    }

    public static void setCurrent(DeserializingMessage message) {
        current.set(message);
    }

    public static DeserializingMessage getCurrent() {
        return current.get();
    }

    public static void removeCurrent() {
        current.remove();
    }

    @Override
    public String toString() {
        return messageFormatter.apply(this);
    }
}
