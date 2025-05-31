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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.common.IdentityProvider;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/**
 * Tracks the lifecycle and identity of a single message handler invocation.
 * <p>
 * This class enables consistent tagging and correlation of all side effects (e.g. metrics, queries, event sourcing,
 * message publication) produced during the execution of a handler. Each invocation is assigned a unique
 * {@link #getId() invocation ID} which is automatically added to metadata and logging contexts to trace functional and
 * non-functional effects back to the triggering message.
 *
 * <h2>Automatic Invocation Wrapping</h2>
 * The Flux Capacitor client automatically wraps all handler invocations using this class. This includes:
 * <ul>
 *     <li>Local handlers (i.e. message handling in the publishing thread)</li>
 *     <li>Tracked handlers (i.e. message tracking via the Flux platform)</li>
 * </ul>
 * <p>
 * As a result, developers typically do not need to call {@link #performInvocation(Callable)} directly,
 * unless they are manually invoking a handler outside of the Flux infrastructure.
 *
 * <h2>Usage</h2>
 * When used manually, wrap handler logic with {@link #performInvocation(Callable)} to activate an invocation context:
 *
 * <pre>{@code
 * Invocation.performInvocation(() -> {
 *     // handler logic
 *     FluxCapacitor.publishEvent(new SomeEvent());
 *     return result;
 * });
 * }</pre>
 * <p>
 * This ensures:
 * <ul>
 *     <li>A consistent invocation ID is available throughout the thread</li>
 *     <li>Any emitted messages, metrics, or queries can include that ID as a correlation token</li>
 *     <li>Callbacks can be registered via {@link #whenHandlerCompletes(BiConsumer)} to react to success/failure</li>
 * </ul>
 *
 * @see #performInvocation(Callable)
 * @see #getCurrent()
 * @see #whenHandlerCompletes(BiConsumer)
 */
@Value
public class Invocation {

    private static final ThreadLocal<Invocation> current = new ThreadLocal<>();
    @Getter(lazy = true)
    String id = IdentityProvider.defaultIdentityProvider.nextTechnicalId();
    transient List<BiConsumer<Object, Throwable>> callbacks = new ArrayList<>();

    /**
     * Wraps the given {@link Callable} in an invocation context.
     * <p>
     * This method ensures that callbacks registered via {@link #whenHandlerCompletes(BiConsumer)} are executed
     * upon completion of the callable.
     *
     * @param callable the task to run
     * @return the callable result
     */
    @SneakyThrows
    public static <V> V performInvocation(Callable<V> callable) {
        if (current.get() != null) {
            return callable.call();
        }
        Invocation invocation = new Invocation();
        current.set(invocation);
        try {
            V result = callable.call();
            current.remove();
            invocation.getCallbacks().forEach(c -> c.accept(result, null));
            return result;
        } catch (Throwable e) {
            current.remove();
            invocation.getCallbacks().forEach(c -> c.accept(null, e));
            throw e;
        }
    }

    /**
     * Returns the current {@code Invocation} bound to this thread, or {@code null} if none exists.
     */
    public static Invocation getCurrent() {
        return current.get();
    }

    /**
     * Registers a callback to be executed when the current handler invocation completes.
     * <p>
     * If no invocation is active, the callback is executed immediately with {@code null} values.
     *
     * @param callback the handler result/error consumer
     * @return a {@link Registration} handle to cancel the callback
     */
    public static Registration whenHandlerCompletes(BiConsumer<Object, Throwable> callback) {
        Invocation invocation = current.get();
        if (invocation == null) {
            callback.accept(null, null);
            return Registration.noOp();
        } else {
            return invocation.registerCallback(callback);
        }
    }

    private Registration registerCallback(BiConsumer<Object, Throwable> callback) {
        callbacks.add(callback);
        return () -> callbacks.remove(callback);
    }
}
