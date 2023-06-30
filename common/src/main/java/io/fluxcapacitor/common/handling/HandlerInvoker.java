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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiFunction;

public interface HandlerInvoker {

    Object getTarget();

    Executable getMethod();

    <A extends Annotation> A getMethodAnnotation();

    boolean expectResult();

    boolean isPassive();

    default Object invoke() {
        return invoke((first, second) -> {
            @SuppressWarnings("unchecked")
            ArrayList<Object> combination = first instanceof ArrayList<?>
                    ? (ArrayList<Object>) first : first instanceof Collection<?>
                    ? new ArrayList<>((Collection<?>) first) : new ArrayList<>(Collections.singletonList(first));
            if (second instanceof Collection<?>) {
                combination.addAll((Collection<?>) second);
            } else {
                combination.add(second);
            }
            return combination;
        });
    }

    Object invoke(BiFunction<Object, Object, Object> combiner);

    default HandlerInvoker combine(HandlerInvoker second) {
        return new DelegatingHandlerInvoker(this) {
            @Override
            public Object invoke(BiFunction<Object, Object, Object> combiner) {
                return combiner.apply(delegate.invoke(), second.invoke());
            }
        };
    }

    @AllArgsConstructor
    abstract class DelegatingHandlerInvoker implements HandlerInvoker {
        protected final HandlerInvoker delegate;

        @Override
        public Object getTarget() {
            return delegate.getTarget();
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
