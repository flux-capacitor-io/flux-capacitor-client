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

import lombok.AllArgsConstructor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

public interface HandlerInvoker {

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

    default HandlerInvoker andThen(HandlerInvoker other) {
        return new DelegatingHandlerInvoker(this) {
            @Override
            public Object invoke(BiFunction<Object, Object, Object> combiner) {
                return combiner.apply(delegate.invoke(), other.invoke());
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

}
