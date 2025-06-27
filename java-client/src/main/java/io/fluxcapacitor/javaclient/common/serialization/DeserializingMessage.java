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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.ThrowingConsumer;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Optional.ofNullable;

/**
 * Wrapper for a {@link Message} that supports lazy deserialization, context caching, type adaptation, and batch-level
 * execution utilities.
 * <p>
 * {@code DeserializingMessage} combines a {@link SerializedMessage} with deserialization and routing logic while
 * maintaining the original message context (type, topic, metadata, and payload).
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Supports on-demand deserialization of a {@link Message}</li>
 *   <li>Provides thread-local access to the message that is currently being handled</li>
 *   <li>Allows attaching resources to the current message or message batch/li>
 * </ul>
 *
 * @see Message
 * @see SerializedMessage
 * @see Serializer
 */
@Value
@Slf4j
@AllArgsConstructor(access = AccessLevel.NONE)
@NonFinal
public class DeserializingMessage implements HasMessage {
    /**
     * The formatter used to produce a human-readable representation of this message, primarily for logging or
     * debugging. By default, this uses {@link MessageFormatter#DEFAULT}.
     *
     * <p>
     * In advanced scenarios, users may replace this field with a custom {@link MessageFormatter} implementation to
     * modify how deserializing messages are rendered (e.g., to include metadata or correlation IDs).
     */
    public static MessageFormatter messageFormatter = MessageFormatter.DEFAULT;

    private static final ThreadLocal<Set<Consumer<Throwable>>> batchCompletionHandlers = new ThreadLocal<>();
    private static final ThreadLocal<Map<Object, Object>> batchResources = new ThreadLocal<>();

    /**
     * Thread-local holder for the message currently being handled.
     * <p>
     * The {@code current} field is automatically set when a {@link DeserializingMessage} is being processed
     * (e.g., inside a handler or during interceptor execution). This allows other components to access
     * metadata about the active message (such as message ID, index, or payload) without needing to pass it explicitly.
     *
     * <p>Typical use cases include:
     * <ul>
     *   <li>Determining the source or context of a command, event, or query</li>
     *   <li>Extracting routing or correlation metadata</li>
     *   <li>Constructing exceptions or results that are scoped to the current message</li>
     * </ul>
     */
    private static final ThreadLocal<DeserializingMessage> current = new ThreadLocal<>();

    @Getter(AccessLevel.NONE)
    DeserializingObject<byte[], SerializedMessage> delegate;
    MessageType messageType;
    String topic;

    @Getter(AccessLevel.NONE)
    @NonFinal
    Message message;

    @Getter(AccessLevel.NONE)
    transient Serializer serializer;

    @Getter(AccessLevel.NONE)
    @NonFinal
    SerializedMessage serializedMessage;

    @Getter(value = AccessLevel.NONE)
    @NonFinal
    transient Map<Class<?>, Object> context;

    public DeserializingMessage(SerializedMessage message, Function<Type, Object> payload,
                                MessageType messageType, String topic, Serializer serializer) {
        this(new DeserializingObject<>(message, payload), messageType, topic, serializer);
    }

    public DeserializingMessage(DeserializingObject<byte[], SerializedMessage> delegate, MessageType messageType,
                                String topic, Serializer serializer) {
        this.delegate = delegate;
        this.messageType = messageType;
        this.topic = topic;
        this.serializer = serializer;
    }

    public DeserializingMessage(@NonNull Message message, MessageType messageType, Serializer serializer) {
        this(message, messageType, null, serializer);
    }

    public DeserializingMessage(@NonNull Message message, MessageType messageType, String topic,
                                Serializer serializer) {
        this.messageType = messageType;
        this.topic = topic;
        this.message = message;
        this.serializer = serializer;
        this.delegate = null;
    }

    protected DeserializingMessage(@NonNull DeserializingMessage input) {
        this.messageType = input.messageType;
        this.topic = input.topic;
        this.message = input.message;
        this.serializer = input.serializer;
        this.delegate = input.delegate;
        this.serializedMessage = input.serializedMessage;
    }

    /*
        Message level
     */

    public void run(Consumer<DeserializingMessage> task) {
        apply(m -> {
            task.accept(m);
            return null;
        });
    }

    public <T> T apply(Function<DeserializingMessage, T> action) {
        return handleBatch(Stream.of(this)).map(action).toList().get(0);
    }

    @Override
    public Message toMessage() {
        if (message == null) {
            message = asMessage();
        }
        return message;
    }

    private Message asMessage() {
        Message m = new Message(getPayload(), getMetadata(), getMessageId(), getTimestamp());
        return switch (messageType) {
            case SCHEDULE -> new Schedule(
                    m.getPayload(), m.getMetadata(), m.getMessageId(), m.getTimestamp(),
                    m.getMetadata().get(Schedule.scheduleIdMetadataKey),
                    ofNullable(getIndex()).map(IndexUtils::timestampFromIndex).orElseGet(FluxCapacitor::currentTime));
            case WEBREQUEST -> new WebRequest(m);
            case WEBRESPONSE -> new WebResponse(m);
            default -> m;
        };
    }

    @Override
    public Metadata getMetadata() {
        return ofNullable(delegate).map(DeserializingObject::getSerializedObject)
                .map(SerializedMessage::getMetadata)
                .or(() -> ofNullable(message).map(Message::getMetadata)).orElse(null);
    }

    public DeserializingMessage withMetadata(Metadata metadata) {
        return ofNullable(delegate).map(d -> new DeserializingMessage(
                        d.getSerializedObject().withMetadata(metadata), d.getObjectFunction(), messageType, topic, serializer))
                .orElseGet(
                        () -> new DeserializingMessage(message.withMetadata(metadata), messageType, topic, serializer));
    }

    public DeserializingMessage withPayload(Object payload) {
        return new DeserializingMessage(toMessage().withPayload(payload), messageType, topic, serializer);
    }

    @Override
    public String getMessageId() {
        return ofNullable(delegate).map(DeserializingObject::getSerializedObject)
                .map(SerializedMessage::getMessageId)
                .or(() -> ofNullable(message).map(Message::getMessageId)).orElse(null);
    }

    public Long getIndex() {
        return ofNullable(delegate).map(DeserializingObject::getSerializedObject)
                .map(SerializedMessage::getIndex).orElseGet(() -> message instanceof Schedule
                        ? IndexUtils.indexFromTimestamp(((Schedule) message).getDeadline()) : null);
    }

    @Override
    public Instant getTimestamp() {
        return ofNullable(delegate).map(DeserializingObject::getSerializedObject)
                .map(SerializedMessage::getTimestamp).map(Instant::ofEpochMilli)
                .or(() -> ofNullable(message).map(Message::getTimestamp)).orElse(null);
    }

    public boolean isDeserialized() {
        return ofNullable(delegate).map(DeserializingObject::isDeserialized).orElse(true);
    }

    @Override
    public <V> V getPayload() {
        return ofNullable(delegate).<V>map(DeserializingObject::getPayload)
                .or(() -> ofNullable(message).map(Message::getPayload)).orElse(null);
    }

    @Override
    public <R> R getPayloadAs(Type type) {
        return ofNullable(delegate).map(d -> d.<R>getPayloadAs(type))
                .orElseGet(() -> ofNullable(message).map(m -> m.<R>getPayloadAs(type)).orElse(null));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Class<?> getPayloadClass() {
        return ofNullable(delegate).<Class>map(DeserializingObject::getPayloadClass)
                .or(() -> ofNullable(message).map(Message::getPayloadClass)).orElse(Void.class);
    }

    public String getType() {
        return ofNullable(delegate).map(DeserializingObject::getType)
                .or(() -> ofNullable(message).map(m -> m.getPayloadClass().getName())).orElse(null);
    }

    public SerializedMessage getSerializedObject() {
        if (delegate != null) {
            return delegate.getSerializedObject();
        }
        if (serializedMessage == null) {
            serializedMessage = message.serialize(serializer);
        }
        return serializedMessage;
    }

    public DeserializingMessage withData(Data<byte[]> data) {
        var serializedMessage = getSerializedObject().withData(data);
        return serializer.deserializeMessage(serializedMessage, messageType);
    }

    @SuppressWarnings("unchecked")
    @Synchronized
    public <T> T computeContextIfAbsent(Class<T> contextKey, Function<DeserializingMessage, ? extends T> provider) {
        if (context == null) {
            context = new ConcurrentHashMap<>();
        }
        return (T) context.computeIfAbsent(contextKey, k -> provider.apply(this));
    }

    /**
     * Returns the current {@link DeserializingMessage} being processed in this thread, or {@code null} if none is set.
     *
     * <p>This method provides direct (nullable) access to the thread-local message context. Prefer {@link #getOptionally()}
     * when you want to safely handle absence of context.
     *
     * <p>Note: This method should typically be called only inside handler code or interceptors
     * where a {@code DeserializingMessage} is known to be active.
     *
     * @return the current message or {@code null} if no message is being processed
     * @see #getOptionally()
     */
    public static DeserializingMessage getCurrent() {
        return current.get();
    }

    /**
     * Returns the current {@link DeserializingMessage} being processed in this thread, if available.
     *
     * <p>This method is safe to call in any thread and will return {@link Optional#empty()} if no message is currently being handled.
     * It is particularly useful for utility classes or exception handlers that want to conditionally access message metadata.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Optional<DeserializingMessage> message = DeserializingMessage.getOptionally();
     * message.map(DeserializingMessage::getPayloadType)
     *        .ifPresent(type -> log.debug("Handling message of type {}", type));
     * }</pre>
     *
     * @return an {@link Optional} containing the current message or empty if none is set
     * @see #getCurrent()
     */
    public static Optional<DeserializingMessage> getOptionally() {
        return Optional.ofNullable(current.get());
    }

    @Override
    public String toString() {
        return messageFormatter.apply(this);
    }
    
    
    /*
        Batch level
     */

    public static Stream<DeserializingMessage> handleBatch(Stream<DeserializingMessage> batch) {
        return StreamSupport.stream(new MessageSpliterator(batch.spliterator()), false);
    }

    @SneakyThrows
    public static void whenBatchCompletes(ThrowingConsumer<Throwable> executable) {
        if (current.get() == null) {
            executable.accept(null);
        } else {
            if (batchCompletionHandlers.get() == null) {
                batchCompletionHandlers.set(new LinkedHashSet<>());
            }
            batchCompletionHandlers.get().add(ObjectUtils.asConsumer(executable));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <K, V> V computeForBatch(K key, BiFunction<? super K, ? super V, ? extends V> function) {
        return (V) getBatchResources().compute(key, (BiFunction) function);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <K, V> V computeForBatchIfAbsent(K key, Function<? super K, ? extends V> function) {
        return (V) getBatchResources().computeIfAbsent(key, (Function) function);
    }

    @SuppressWarnings("unchecked")
    public static <V> V getBatchResource(Object key) {
        return (V) getBatchResources().get(key);
    }

    @SuppressWarnings("unchecked")
    public static <V> V getBatchResourceOrDefault(Object key, V defaultValue) {
        return (V) getBatchResources().getOrDefault(key, defaultValue);
    }

    private static Map<Object, Object> getBatchResources() {
        if (batchResources.get() == null) {
            batchResources.set(new HashMap<>());
        }
        return batchResources.get();
    }

    protected static class MessageSpliterator extends Spliterators.AbstractSpliterator<DeserializingMessage> {
        private final Spliterator<DeserializingMessage> upStream;

        public MessageSpliterator(Spliterator<DeserializingMessage> upStream) {
            super(upStream.estimateSize(), upStream.characteristics());
            this.upStream = upStream;
        }

        @Override
        public boolean tryAdvance(Consumer<? super DeserializingMessage> action) {
            boolean hadNext;
            try {
                hadNext = upStream.tryAdvance(d -> {
                    DeserializingMessage previous = getCurrent();
                    try {
                        current.set(d);
                        action.accept(d);
                    } finally {
                        current.set(previous);
                    }
                });
            } catch (Throwable e) {
                onBatchCompletion(e);
                throw e;
            }
            if (!hadNext && getCurrent() == null) {
                onBatchCompletion(null);
            }
            return hadNext;
        }

        protected void onBatchCompletion(Throwable error) {
            try {
                ofNullable(batchCompletionHandlers.get()).ifPresent(handlers -> {
                    batchCompletionHandlers.remove();
                    handlers.forEach(h -> h.accept(error));
                });
            } finally {
                batchResources.remove();
                batchCompletionHandlers.remove();
            }
        }

    }
}
