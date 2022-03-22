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
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.modeling.AggregateIdResolver;
import io.fluxcapacitor.javaclient.modeling.AggregateTypeResolver;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
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
import lombok.Value;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
@AllArgsConstructor
@Slf4j
public class DeserializingMessage {
    public static MessageFormatter messageFormatter = MessageFormatter.DEFAULT;
    public static List<ParameterResolver<? super DeserializingMessage>> defaultParameterResolvers =
            Arrays.asList(new DeserializingMessageParameterResolver(),
                          new MetadataParameterResolver(),
                          new MessageParameterResolver(), new AggregateIdResolver(),
                          new AggregateTypeResolver(), new UserParameterResolver(),
                          new WebPayloadParameterResolver(), new PayloadParameterResolver());

    private static final ThreadLocal<Set<Consumer<Throwable>>> messageCompletionHandlers = new ThreadLocal<>();
    private static final ThreadLocal<Map<Object, Object>> messageResources = new ThreadLocal<>();
    private static final ThreadLocal<Set<Consumer<Throwable>>> batchCompletionHandlers = new ThreadLocal<>();
    private static final ThreadLocal<Map<Object, Object>> batchResources = new ThreadLocal<>();
    private static final ThreadLocal<DeserializingMessage> current = new ThreadLocal<>();

    @Delegate
    DeserializingObject<byte[], SerializedMessage> delegate;
    MessageType messageType;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    Message message = toMessage();

    public DeserializingMessage(SerializedMessage message, Function<Class<?>, Object> payload,
                                MessageType messageType) {
        this(new DeserializingObject<>(message, payload), messageType);
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
        return delegate.getSerializedObject().getMetadata();
    }

    public String getMessageId() {
        return getSerializedObject().getMessageId();
    }

    public Long getIndex() {
        return getSerializedObject().getIndex();
    }

    public Instant getTimestamp() {
        return Instant.ofEpochMilli(getSerializedObject().getTimestamp());
    }

    public Message asMessage() {
        return getMessage();
    }

    private Message toMessage() {
        Message message = new Message(getPayload(), getMetadata(), getMessageId(), getTimestamp());
        switch (getMessageType()) {
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

    public static DeserializingMessage getCurrent() {
        return current.get();
    }

    public static void whenHandlerCompletes(Consumer<Throwable> handler) {
        if (current.get() == null) {
            handler.accept(null);
        } else {
            if (messageCompletionHandlers.get() == null) {
                messageCompletionHandlers.set(new LinkedHashSet<>());
            }
            messageCompletionHandlers.get().add(handler);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <K, V> V computeForMessage(K key, BiFunction<? super K, ? super V, ? extends V> function) {
        return (V) getMessageResources().compute(key, (BiFunction) function);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <K, V> V computeForMessageIfAbsent(K key, Function<? super K, ? extends V> function) {
        return (V) getMessageResources().computeIfAbsent(key, (Function) function);
    }

    @SuppressWarnings("unchecked")
    public static <V> V getMessageResource(Object key) {
        return (V) getMessageResources().get(key);
    }

    @SuppressWarnings("unchecked")
    public static <V> V getMessageResourceOrDefault(Object key, V defaultValue) {
        return (V) getMessageResources().getOrDefault(key, defaultValue);
    }

    private static Map<Object, Object> getMessageResources() {
        if (messageResources.get() == null) {
            messageResources.set(new HashMap<>());
        }
        return messageResources.get();
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
                        onMessageCompletion(null, previous);
                    } catch (Throwable e) {
                        onMessageCompletion(e, previous);
                        throw e;
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

        protected void onMessageCompletion(Throwable error, DeserializingMessage previous) {
            try {
                if (previous == null) {
                    try {
                        ofNullable(messageCompletionHandlers.get()).ifPresent(handlers -> {
                            messageCompletionHandlers.remove();
                            handlers.forEach(h -> h.accept(error));
                        });
                    } finally {
                        messageResources.remove();
                        messageCompletionHandlers.remove();
                    }
                }
            } finally {
                current.set(previous);
            }

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
