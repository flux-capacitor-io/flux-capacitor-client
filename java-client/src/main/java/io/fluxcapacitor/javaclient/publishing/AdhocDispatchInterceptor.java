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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.metrics.DisableMetrics;
import lombok.SneakyThrows;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * A {@link DispatchInterceptor} that enables thread-local, dynamically scoped interceptors during message dispatch.
 *
 * <p>This class allows you to temporarily override dispatch behavior for one or more {@link MessageType}s within
 * a specific scope (such as during the invocation of a handler or message batch). It is particularly useful for use
 * cases where message output needs to be suppressed, enriched, rerouted, or filtered conditionally.
 *
 * <p>Adhoc interceptors are applied using
 * {@link #runWithAdhocInterceptor(Runnable, DispatchInterceptor, MessageType...)} or
 * {@link #runWithAdhocInterceptor(Callable, DispatchInterceptor, MessageType...)}.
 *
 * <h2>Typical Use Case: Disabling Metrics</h2>
 * A common use case is disabling outbound metric messages for specific message consumers via {@link DisableMetrics}
 * which internally uses this behavior:
 *
 * <pre>{@code
 * @Consumer(batchInterceptors = DisableMetrics.class)
 * public class OrderHandler {
 *     @HandleEvent
 *     public void on(OrderPlaced event) {
 *         // This handler will execute with METRICS messages disabled
 *     }
 * }
 * }</pre>
 *
 * <h2>Disabling All Adhoc Interceptors</h2>
 * This behavior can be disabled globally by calling
 * {@link io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder#disableAdhocDispatchInterceptor()} during Flux
 * client setup. This is useful in highly constrained environments or when performance is critical.
 *
 * @see DispatchInterceptor
 * @see DisableMetrics
 */
public class AdhocDispatchInterceptor implements DispatchInterceptor {

    private static final ThreadLocal<Map<MessageType, DispatchInterceptor>> delegates = new ThreadLocal<>();

    /**
     * Returns the current thread-local ad hoc interceptor for the given {@link MessageType}, if present.
     *
     * @param messageType The message type to look up.
     * @return An optional interceptor for the specified message type.
     */
    public static Optional<? extends DispatchInterceptor> getAdhocInterceptor(MessageType messageType) {
        return Optional.ofNullable(delegates.get()).map(map -> map.get(messageType));
    }

    /**
     * Executes the given {@link Callable} while temporarily enabling the provided interceptor for the specified message
     * types.
     * <p>
     * After the task completes (or throws), the previous interceptors are automatically restored. If no message types
     * are specified, the interceptor is applied to all {@link MessageType}s.
     *
     * @param task             The task to run.
     * @param adhocInterceptor The interceptor to apply during execution.
     * @param messageTypes     The message types for which to apply the interceptor. If empty, all types are used.
     * @param <T>              The return type of the task.
     * @return The result from the task.
     */
    @SneakyThrows
    public static <T> T runWithAdhocInterceptor(Callable<T> task, DispatchInterceptor adhocInterceptor,
                                                MessageType... messageTypes) {
        Map<MessageType, DispatchInterceptor> previous = delegates.get();
        Map<MessageType, DispatchInterceptor> merged = Optional.ofNullable(previous).orElseGet(HashMap::new);
        Stream<MessageType> typeStream =
                (messageTypes.length == 0 ? EnumSet.allOf(MessageType.class).stream() : Arrays.stream(messageTypes));
        typeStream.forEach(messageType -> merged.compute(
                messageType, (t, i) -> i == null ? adhocInterceptor : i.andThen(adhocInterceptor)));
        try {
            delegates.set(merged);
            return task.call();
        } finally {
            delegates.set(previous);
        }
    }

    /**
     * Executes the given {@link Runnable} while temporarily enabling the provided interceptor for the specified message
     * types.
     * <p>
     * After the task completes (or throws), the previous interceptors are automatically restored. If no message types
     * are specified, the interceptor is applied to all {@link MessageType}s.
     *
     * @param task             The task to run.
     * @param adhocInterceptor The interceptor to apply during execution.
     * @param messageTypes     The message types for which to apply the interceptor. If empty, all types are used.
     */
    public static void runWithAdhocInterceptor(Runnable task, DispatchInterceptor adhocInterceptor,
                                               MessageType... messageTypes) {
        runWithAdhocInterceptor(() -> {
            task.run();
            return null;
        }, adhocInterceptor, messageTypes);
    }

    /**
     * Intercepts a message before dispatch for the given message type and topic. Delegates to any registered ad hoc
     * interceptor for the current thread.
     */
    @Override
    public Message interceptDispatch(Message message, MessageType messageType, String topic) {
        var adhocInterceptor = getAdhocInterceptor(messageType);
        return adhocInterceptor.isPresent() ? adhocInterceptor.get().interceptDispatch(message, messageType, topic) :
                message;
    }

    /**
     * Optionally modifies the serialized message before dispatch, delegating to any registered ad hoc interceptor for
     * the current thread.
     */
    @Override
    public SerializedMessage modifySerializedMessage(SerializedMessage serializedMessage, Message message,
                                                     MessageType messageType, String topic) {
        var adhocInterceptor = getAdhocInterceptor(messageType);
        return adhocInterceptor.isPresent() ?
                adhocInterceptor.get().modifySerializedMessage(serializedMessage, message, messageType, topic) :
                serializedMessage;
    }

    /**
     * Optionally monitors a dispatched message using any registered ad hoc interceptor for the current thread.
     */
    @Override
    public void monitorDispatch(Message message, MessageType messageType, String topic) {
        getAdhocInterceptor(messageType).ifPresent(i -> i.monitorDispatch(message, messageType, topic));
    }
}
