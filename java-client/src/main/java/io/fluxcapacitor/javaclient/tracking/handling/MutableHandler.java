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

import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerMatcher;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * A specialized {@link Handler} that maintains and mutates an internal delegate instance.
 * <p>
 * This handler is designed for use cases where the state of the handler may evolve dynamically
 * across message invocations. It is particularly suited for temporarily scoped objects (e.g. sagas, user sessions).
 *
 * <h2>Behavior</h2>
 * When a handler method returns an instance of the handler’s target class, this instance is stored
 * as the new delegate:
 * <pre>{@code
 * Object result = method.invoke(target);
 * if (result instanceof MyHandler) {
 *     target = result; // promote new version
 * }
 * }</pre>
 * <p>
 * If the method returns {@code null} and the method's return type matches the target class, the internal
 * target is cleared, and any registered delete callbacks are triggered.
 *
 * <h2>Lifecycle Hooks</h2>
 * A delete callback can be registered using {@link #onDelete(Runnable)} to perform cleanup when
 * the handler deletes its state (i.e., returns {@code null}).
 *
 * @param <M> the message type this handler supports
 */
@AllArgsConstructor
public class MutableHandler<M> implements Handler<M> {
    @Getter
    private final Class<?> targetClass;
    private final HandlerMatcher<Object, M> handlerMatcher;
    private final boolean returnTargetInstance;

    private final Set<Runnable> onDeleteCallbacks = ConcurrentHashMap.newKeySet();

    @Getter
    private volatile Object target;

    @Override
    public Optional<HandlerInvoker> getInvoker(M message) {
        return handlerMatcher.getInvoker(target, message)
                .map(h -> new HandlerInvoker.DelegatingHandlerInvoker(h) {
                    @Override
                    public Object invoke(BiFunction<Object, Object, Object> combiner) {
                        Object result = delegate.invoke(combiner);
                        if (targetClass.isInstance(result)) {
                            target = result;
                            if (!returnTargetInstance) {
                                return null;
                            }
                        } else if (result == null && expectResult() && getMethod() instanceof Method m
                                   && (targetClass.isAssignableFrom(m.getReturnType())
                                       || m.getReturnType().isAssignableFrom(targetClass))) {
                            target = null;
                            onDeleteCallbacks.forEach(ObjectUtils::tryRun);
                        }
                        return result;
                    }
                });
    }

    public MutableHandler<M> instantiateTarget() {
        target = ReflectionUtils.asInstance(targetClass);
        return this;
    }

    public boolean isEmpty() {
        return target == null;
    }

    public Registration onDelete(Runnable callback) {
        onDeleteCallbacks.add(callback);
        return () -> onDeleteCallbacks.remove(callback);
    }
}
