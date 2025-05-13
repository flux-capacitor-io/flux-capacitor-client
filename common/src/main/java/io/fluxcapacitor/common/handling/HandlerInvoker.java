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
import java.lang.reflect.Executable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

import static io.fluxcapacitor.common.ObjectUtils.asCallable;

public interface HandlerInvoker {

    static HandlerInvoker noOp() {
        return SimpleInvoker.noOpInvoker;
    }

    static HandlerInvoker run(ThrowingRunnable task) {
        return new SimpleInvoker(asCallable(task));
    }

    static HandlerInvoker call(Callable<?> task) {
        return new SimpleInvoker(task);
    }

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

    Class<?> getTargetClass();

    Executable getMethod();

    <A extends Annotation> A getMethodAnnotation();

    boolean expectResult();

    boolean isPassive();

    default Object invoke() {
        return invoke((firstResult, secondResult) -> firstResult);
    }

    Object invoke(BiFunction<Object, Object, Object> resultCombiner);

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
