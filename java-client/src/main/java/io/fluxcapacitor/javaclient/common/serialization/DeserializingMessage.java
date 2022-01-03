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
import io.fluxcapacitor.common.Registration;
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
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.time.Instant.ofEpochMilli;
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

    private static final ThreadLocal<Collection<Runnable>> messageCompletionHandlers = new ThreadLocal<>();
    private static final ThreadLocal<Collection<Runnable>> batchCompletionHandlers = new ThreadLocal<>();
    private static final ThreadLocal<Map<Object, Object>> batchResources = new ThreadLocal<>();
    private static final ThreadLocal<DeserializingMessage> current = new ThreadLocal<>();

    @Delegate
    DeserializingObject<byte[], SerializedMessage> delegate;
    MessageType messageType;

    public DeserializingMessage(SerializedMessage message, Function<Class<?>, Object> payload,
                                MessageType messageType) {
        this(new DeserializingObject<>(message, payload), messageType);
    }

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

    public Message toMessage() {
        Message message = new Message(delegate.getPayload(), getMetadata(),
                                      delegate.getSerializedObject().getMessageId(),
                                      ofEpochMilli(delegate.getSerializedObject().getTimestamp()));
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

    public static DeserializingMessage getCurrent() {
        return current.get();
    }

    public static Registration whenBatchCompletes(Runnable handler) {
        if (current.get() == null) {
            try {
                return Registration.noOp();
            } finally {
                handler.run();
            }
        }

        if (batchCompletionHandlers.get() == null) {
            batchCompletionHandlers.set(new ArrayList<>());
        }
        Collection<Runnable> handlers = batchCompletionHandlers.get();
        handlers.add(handler);
        return () -> handlers.remove(handler);
    }

    public static void whenMessageCompletes(Runnable handler) {
        if (current.get() == null) {
            try {
                return;
            } finally {
                handler.run();
            }
        }
        if (messageCompletionHandlers.get() == null) {
            messageCompletionHandlers.set(new ArrayList<>());
        }
        Collection<Runnable> handlers = messageCompletionHandlers.get();
        handlers.add(handler);
    }


    public static Stream<DeserializingMessage> handleBatch(Stream<DeserializingMessage> batch) {
        return StreamSupport.stream(new MessageSpliterator(batch.spliterator()), false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <K, V> V computeForBatch(K key, BiFunction<? super K, ? super V, ? extends V> function) {
        return (V) getResources().compute(key, (BiFunction) function);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <K, V> V computeForBatchIfAbsent(K key, Function<? super K, ? extends V> function) {
        return (V) getResources().computeIfAbsent(key, (Function) function);
    }

    @SuppressWarnings("unchecked")
    public static <V> V getBatchResource(Object key) {
        return (V) getResources().get(key);
    }

    private static Map<Object, Object> getResources() {
        if (batchResources.get() == null) {
            batchResources.set(new HashMap<>());
        }
        return batchResources.get();
    }

    @Override
    public String toString() {
        return messageFormatter.apply(this);
    }

    private static void setCurrent(DeserializingMessage message) {
        current.set(message);
        if (message == null) {
            ofNullable(messageCompletionHandlers.get()).ifPresent(handlers -> {
                messageCompletionHandlers.remove();
                handlers.forEach(Runnable::run);
            });
        }
    }

    private static class MessageSpliterator extends Spliterators.AbstractSpliterator<DeserializingMessage> {
        private final Spliterator<DeserializingMessage> upStream;

        public MessageSpliterator(Spliterator<DeserializingMessage> upStream) {
            super(upStream.estimateSize(), upStream.characteristics());
            this.upStream = upStream;
        }

        @Override
        public boolean tryAdvance(Consumer<? super DeserializingMessage> action) {
            boolean hadNext = upStream.tryAdvance(d -> {
                DeserializingMessage previous = getCurrent();
                try {
                    setCurrent(d);
                    action.accept(d);
                } finally {
                    setCurrent(previous);
                }
            });
            if (!hadNext && DeserializingMessage.getCurrent() == null) {
                try {
                    ofNullable(batchCompletionHandlers.get()).ifPresent(handlers -> {
                        batchCompletionHandlers.remove();
                        handlers.forEach(Runnable::run);
                    });
                } finally {
                    batchResources.remove();
                    batchCompletionHandlers.remove();
                }
            }
            return hadNext;
        }
    }
}
