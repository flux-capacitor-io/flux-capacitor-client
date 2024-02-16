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

import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerMatcher;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.Entry;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.ViewRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Delegate;
import lombok.experimental.FieldDefaults;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperties;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPropertyName;
import static io.fluxcapacitor.javaclient.FluxCapacitor.generateId;
import static java.util.stream.Collectors.toSet;

@Getter
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ViewHandler implements Handler<DeserializingMessage> {
    Class<?> targetClass;
    HandlerMatcher<Object, DeserializingMessage> handlerMatcher;
    ViewRepository repository;

    @Getter(lazy = true)
    Collection<String> associationProperties = getAnnotatedProperties(getTargetClass(), Association.class).stream()
            .map(member -> getAnnotation(member, Association.class).map(Association::value)
                    .filter(name -> !name.isBlank()).orElseGet(() -> getPropertyName(member))).collect(toSet());

    @Override
    public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
        if (!handlerMatcher.canHandle(message)) {
            return Optional.empty();
        }
        var matches = repository.findByAssociation(associations(message));
        if (matches.isEmpty()) {
            return handlerMatcher.getInvoker(null, message)
                    .map(i -> new ViewInvoker(i, null));
        }
        HandlerInvoker result = null;
        for (Entry<?> entry : matches) {
            var invoker = handlerMatcher.getInvoker(entry.getValue(), message)
                    .map(i -> new ViewInvoker(i, entry));
            if (invoker.isPresent()) {
                result = result == null ? invoker.get() : result.combine(invoker.get());
            }
        }
        return Optional.ofNullable(result);
    }

    protected Collection<String> associations(DeserializingMessage message) {
        return Optional.ofNullable(message.getPayload()).stream()
                .flatMap(p -> getAssociationProperties().stream()
                        .flatMap(property -> ReflectionUtils.readProperty(property, p).stream()))
                .map(Object::toString).collect(toSet());
    }

    @Getter
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    protected class ViewInvoker implements HandlerInvoker {
        @Delegate(excludes = ExcludedMethods.class)
        HandlerInvoker delegate;
        Entry<?> currentEntry;

        @Override
        public Object invoke(BiFunction<Object, Object, Object> combiner) {
            Object result = delegate.invoke(combiner);
            if (delegate.getTargetClass().isInstance(result)) {
                if (currentEntry == null || !Objects.equals(currentEntry.getValue(), result)) {
                    repository.set(result, currentEntry == null ? generateId() : currentEntry.getId());
                }
            }
            if (result == null && expectResult() && getMethod() instanceof Method m
                && (delegate.getTargetClass().isAssignableFrom(m.getReturnType())
                    || m.getReturnType().isAssignableFrom(delegate.getTargetClass()))) {
                if (currentEntry != null) {
                    repository.delete(currentEntry.getId());
                }
            }
            return result;
        }

        private interface ExcludedMethods {
            Object invoke(BiFunction<Object, Object, Object> combiner);
        }

    }
}
