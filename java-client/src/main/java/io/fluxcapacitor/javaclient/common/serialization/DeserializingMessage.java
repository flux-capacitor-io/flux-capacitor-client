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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import io.fluxcapacitor.javaclient.tracking.handling.DeserializingMessageParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.MessageParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.MetadataParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.PayloadParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserParameterResolver;
import io.fluxcapacitor.javaclient.web.WebPayloadParameterResolver;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@Value
@Slf4j
@AllArgsConstructor(access = AccessLevel.NONE)
public class DeserializingMessage {
    public static MessageFormatter messageFormatter = MessageFormatter.DEFAULT;
    public static List<ParameterResolver<? super DeserializingMessage>> defaultParameterResolvers =
            Arrays.asList(new DeserializingMessageParameterResolver(),
                          new MetadataParameterResolver(), new MessageParameterResolver(),
                          new UserParameterResolver(), new WebPayloadParameterResolver(),
                          new PayloadParameterResolver());

    private static final ThreadLocal<Set<Consumer<Throwable>>> batchCompletionHandlers = new ThreadLocal<>();
    private static final ThreadLocal<Map<Object, Object>> batchResources = new ThreadLocal<>();
    private static final ThreadLocal<DeserializingMessage> current = new ThreadLocal<>();

    @Getter(AccessLevel.NONE)
    DeserializingObject<byte[], SerializedMessage> delegate;
    MessageType messageType;

    @Getter(AccessLevel.NONE)
    @NonFinal
    Message message;

    @Getter(AccessLevel.NONE)
    transient Serializer serializer;

    @Getter(AccessLevel.NONE)
    @NonFinal
    SerializedMessage serializedMessage;

    public DeserializingMessage(SerializedMessage message, Function<Class<?>, Object> payload,
                                MessageType messageType) {
        this(new DeserializingObject<>(message, payload), messageType);
    }

    public DeserializingMessage(DeserializingObject<byte[], SerializedMessage> delegate, MessageType messageType) {
        this.delegate = delegate;
        this.messageType = messageType;
        this.serializer = null;
    }

    public DeserializingMessage(@NonNull Message message, MessageType messageType, Serializer serializer) {
        this.messageType = messageType;
        this.message = message;
        this.serializer = serializer;
        this.delegate = null;
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
        return handleBatch(Stream.of(this)).map(action).collect(toList()).get(0);
    }

    public Metadata getMetadata() {
        return Optional.ofNullable(delegate).map(DeserializingObject::getSerializedObject)
                .map(SerializedMessage::getMetadata).orElseGet(() -> message.getMetadata());
    }

    public String getMessageId() {
        return Optional.ofNullable(delegate).map(DeserializingObject::getSerializedObject)
                .map(SerializedMessage::getMessageId).orElseGet(() -> message.getMessageId());
    }

    public Long getIndex() {
        return Optional.ofNullable(delegate).map(DeserializingObject::getSerializedObject)
                .map(SerializedMessage::getIndex).orElseGet(() -> message instanceof Schedule
                        ? IndexUtils.indexFromTimestamp(((Schedule) message).getDeadline()) : null);
    }

    public Instant getTimestamp() {
        return Optional.ofNullable(delegate).map(DeserializingObject::getSerializedObject)
                .map(SerializedMessage::getTimestamp).map(Instant::ofEpochMilli)
                .orElseGet(() -> message.getTimestamp());
    }

    public Message toMessage() {
        if (message == null) {
            message = asMessage();
        }
        return message;
    }

    private Message asMessage() {
        Message message = new Message(getPayload(), getMetadata(), getMessageId(), getTimestamp());
        switch (messageType) {
            case SCHEDULE:
                return new Schedule(message);
            case WEBREQUEST:
                return new WebRequest(message);
            case WEBRESPONSE:
                return new WebResponse(message);
            default:
                return message;
        }
    }

    public boolean isDeserialized() {
        return Optional.ofNullable(delegate).map(DeserializingObject::isDeserialized).orElse(true);
    }

    public <V> V getPayload() {
        return Optional.ofNullable(delegate).<V>map(DeserializingObject::getPayload)
                .orElseGet(() -> message.getPayload());
    }

    public <V> V getPayloadAs(Class<V> type) {
        return Optional.ofNullable(delegate).map(d -> d.getPayloadAs(type)).orElseGet(
                () -> JsonUtils.convertValue(message.getPayload(), type));
    }

    @SuppressWarnings("rawtypes")
    public Class<?> getPayloadClass() {
        return Optional.ofNullable(delegate).<Class>map(DeserializingObject::getPayloadClass)
                .orElseGet(() -> message.getPayloadClass());
    }

    public String getType() {
        return Optional.ofNullable(delegate).map(DeserializingObject::getType).orElseGet(
                () -> message.getPayloadClass().getName());
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

    public static DeserializingMessage getCurrent() {
        return current.get();
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

    public static void whenBatchCompletes(Consumer<Throwable> handler) {
        if (current.get() == null) {
            handler.accept(null);
        } else {
            if (batchCompletionHandlers.get() == null) {
                batchCompletionHandlers.set(new LinkedHashSet<>());
            }
            batchCompletionHandlers.get().add(handler);
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
