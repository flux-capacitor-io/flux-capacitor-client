package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.HandlerInspector.MethodHandlerInvoker;
import io.fluxcapacitor.common.handling.MethodInvokerFactory;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.modeling.AggregateIdResolver;
import io.fluxcapacitor.javaclient.modeling.AggregateTypeResolver;
import io.fluxcapacitor.javaclient.tracking.handling.BatchHandlerInvoker;
import io.fluxcapacitor.javaclient.tracking.handling.DeserializingMessageParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.ListParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.MessageParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.MetadataParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.PayloadParameterResolver;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.fluxcapacitor.javaclient.tracking.handling.BatchHandlerInvoker.handlesBatch;
import static java.time.Instant.ofEpochMilli;

@Value
@AllArgsConstructor
@Slf4j
public class DeserializingMessage {
    public static MessageFormatter messageFormatter = MessageFormatter.DEFAULT;
    public static List<ParameterResolver<? super DeserializingMessage>> defaultParameterResolvers =
            Arrays.asList(new DeserializingMessageParameterResolver(), new ListParameterResolver(), 
                          new PayloadParameterResolver(), new MetadataParameterResolver(), 
                          new MessageParameterResolver(), new AggregateIdResolver(), 
                          new AggregateTypeResolver());
    public static MethodInvokerFactory<DeserializingMessage> defaultInvokerFactory = 
            (executable, enclosingType, parameterResolvers) -> handlesBatch(executable) 
                    ? new BatchHandlerInvoker(executable, enclosingType, parameterResolvers) 
                    : new MethodHandlerInvoker<>(executable, enclosingType, parameterResolvers);

    private static final ThreadLocal<Collection<Runnable>> completionHandlers = new ThreadLocal<>();
    private static final ThreadLocal<DeserializingMessage> current = new ThreadLocal<>();
    
    @Delegate
    DeserializingObject<byte[], SerializedMessage> delegate;
    MessageType messageType;

    public DeserializingMessage(SerializedMessage message, Supplier<Object> payload, MessageType messageType) {
        this(new DeserializingObject<>(message, payload), messageType);
    }

    public void run(Consumer<DeserializingMessage> task) {
        apply(m -> {
            task.accept(m);
            return null;
        });
    }

    public <T> T apply(Function<DeserializingMessage, T> task) {
        DeserializingMessage previous = getCurrent();
        try {
            DeserializingMessage.setCurrent(this);
            return task.apply(this);
        } finally {
            setCurrent(previous);
        }
    }

    public Metadata getMetadata() {
        return delegate.getSerializedObject().getMetadata();
    }

    public Message toMessage() {
        return new Message(delegate.getPayload(), getMetadata(),
                           delegate.getSerializedObject().getMessageId(),
                           ofEpochMilli(delegate.getSerializedObject().getTimestamp()));
    }

    public static DeserializingMessage getCurrent() {
        return current.get();
    }

    public static void onComplete(Runnable handler) {
        if (completionHandlers.get() == null) {
            completionHandlers.set(new CopyOnWriteArrayList<>());
        }
        completionHandlers.get().add(handler);
    }

    private static void setCurrent(DeserializingMessage message) {
        Object previous = getCurrent();
        try {
            if (message == null && previous != null) {
                while (completionHandlers.get() != null) {
                    Collection<Runnable> handlers = completionHandlers.get();
                    completionHandlers.remove();
                    handlers.forEach(h -> {
                        try {
                            h.run();
                        } catch (Exception e) {
                            log.warn("Completion handler failed to run", e);
                        }
                    });
                }
            }
        } finally {
            current.set(message);
        }
    }

    @Override
    public String toString() {
        return messageFormatter.apply(this);
    }
}
