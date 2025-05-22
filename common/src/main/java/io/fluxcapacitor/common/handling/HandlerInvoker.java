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

package io.fluxcapacitor.common.handling;

import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.ThrowingRunnable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

import static io.fluxcapacitor.common.ObjectUtils.asCallable;

/**
 * Represents an invocable handler method.
 * <p>
 * A {@code HandlerInvoker} is typically resolved by a {@link Handler} for a specific message, and is responsible for
 * invoking the actual handler method, including any surrounding interceptors or decorators.
 * </p>
 *
 * @see Handler
 */
public interface HandlerInvoker {

    /**
     * Returns a no-op invoker that performs no action and returns {@code null}.
     */
    static HandlerInvoker noOp() {
        return SimpleInvoker.noOpInvoker;
    }

    /**
     * Wraps a {@link ThrowingRunnable} in a {@code HandlerInvoker}.
     *
     * @param task the task to run
     * @return a {@code HandlerInvoker} that runs the given task
     */
    static HandlerInvoker run(ThrowingRunnable task) {
        return new SimpleInvoker(asCallable(task));
    }

    /**
     * Wraps a {@link Callable} in a {@code HandlerInvoker}.
     *
     * @param task the task to call
     * @return a {@code HandlerInvoker} that invokes the given callable
     */
    static HandlerInvoker call(Callable<?> task) {
        return new SimpleInvoker(task);
    }

    /**
     * Joins a list of invokers into a single composite invoker. The result of each invocation is combined using the
     * provided combiner function.
     *
     * @param invokers a list of invokers to join
     * @return an optional containing the combined invoker, or empty if the list is empty
     */
    static Optional<HandlerInvoker> join(List<? extends HandlerInvoker> invokers) {
        if (invokers.isEmpty()) {
            return Optional.empty();
        }
        if (invokers.size() == 1) {
            return Optional.of(invokers.getFirst());
        }
        return Optional.of(new DelegatingHandlerInvoker(invokers.getFirst()) {
            @Override
            public Object invoke(BiFunction<Object, Object, Object> combiner) {
                Object result = delegate.invoke();
                for (int i = 1; i < invokers.size(); i++) {
                    result = combiner.apply(result, invokers.get(i).invoke());
                }
                return result;
            }
        });
    }

    /**
     * Composes this invoker with another to be invoked in a {@code finally} block. The second invoker will always run,
     * even if the first one fails.
     *
     * @param other the invoker to run after this one
     * @return a combined invoker with finalization behavior
     */
    default HandlerInvoker andFinally(HandlerInvoker other) {
        return new DelegatingHandlerInvoker(this) {
            @Override
            public Object invoke(BiFunction<Object, Object, Object> combiner) {
                try {
                    return delegate.invoke();
                } finally {
                    other.invoke();
                }
            }
        };
    }

    /**
     * The target class that contains the handler method.
     *
     * @return the declaring class of the handler
     */
    Class<?> getTargetClass();

    /**
     * The {@link Executable} representing the handler method (can be a static or instance {@link Method} or
     * {@link Constructor}).
     *
     * @return the executable method
     */
    Executable getMethod();

    /**
     * Retrieves a specific annotation from the handler method, if present.
     *
     * @param <A> the annotation type
     * @return the annotation instance, or {@code null} if not found
     */
    <A extends Annotation> A getMethodAnnotation();

    /**
     * Indicates whether the handler method has a return value.
     * <p>
     * This is based on the method's signature: if it returns {@code void}, this returns {@code false};
     * otherwise, it returns {@code true}.
     * </p>
     *
     * @return {@code true} if the method returns a value; {@code false} if it is {@code void}
     */
    boolean expectResult();

    /**
     * Indicates whether this handler operates in passive mode (i.e., results will not be published).
     *
     * @return {@code true} if passive; otherwise {@code false}
     */
    boolean isPassive();

    /**
     * Invokes the handler using the default result-combining strategy (returns the first result).
     *
     * @return the result of the handler invocation
     */
    default Object invoke() {
        return invoke((firstResult, secondResult) -> firstResult);
    }

    /**
     * Invokes the handler and combines results using the given combiner function. Used when aggregating results from
     * multiple invokers (e.g. {@link #join(List)}).
     *
     * @param resultCombiner function to combine multiple results
     * @return the combined result
     */
    Object invoke(BiFunction<Object, Object, Object> resultCombiner);

    /**
     * A {@code HandlerInvoker} that delegates all behavior to another instance. This is commonly used to wrap or extend
     * behavior without altering core logic.
     */
    @AllArgsConstructor
    abstract class DelegatingHandlerInvoker implements HandlerInvoker {
        protected final HandlerInvoker delegate;

        @Override
        public Class<?> getTargetClass() {
            return delegate.getTargetClass();
        }

        @Override
        public Executable getMethod() {
            return delegate.getMethod();
        }

        @Override
        public <A extends Annotation> A getMethodAnnotation() {
            return delegate.getMethodAnnotation();
        }

        @Override
        public boolean expectResult() {
            return delegate.expectResult();
        }

        @Override
        public boolean isPassive() {
            return delegate.isPassive();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    /**
     * A simple invoker backed by a {@link Callable}, typically used for test utilities or framework-internal logic. Not
     * associated with any actual handler method.
     */
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class SimpleInvoker implements HandlerInvoker {
        private static final SimpleInvoker noOpInvoker = new SimpleInvoker(() -> null);

        private static final Executable method = ObjectUtils.call(() -> SimpleInvoker.class.getMethod("invoke"));

        private final Callable<?> callable;

        @Override
        public Class<?> getTargetClass() {
            return SimpleInvoker.class;
        }

        @Override
        public Executable getMethod() {
            return method;
        }

        @Override
        public <A extends Annotation> A getMethodAnnotation() {
            return null;
        }

        @Override
        public boolean expectResult() {
            return false;
        }

        @Override
        public boolean isPassive() {
            return false;
        }

        @Override
        @SneakyThrows
        public Object invoke(BiFunction<Object, Object, Object> combiner) {
            return callable.call();
        }
    }
}
