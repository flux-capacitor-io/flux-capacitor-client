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
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.Entry;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.modeling.HandlerRepository;
import io.fluxcapacitor.javaclient.modeling.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperties;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPropertyName;
import static java.util.stream.Collectors.toSet;

@Getter
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class StatefulHandler implements Handler<DeserializingMessage> {
    Class<?> targetClass;
    HandlerMatcher<Object, DeserializingMessage> handlerMatcher;
    HandlerRepository repository;

    @Getter(lazy = true)
    Set<String> associationProperties = getAnnotatedProperties(getTargetClass(), Association.class).stream()
            .flatMap(member -> getAnnotation(member, Association.class).stream().flatMap(
                    a -> a.value().length > 0 ? Arrays.stream(a.value()) : Stream.of(getPropertyName(member))))
                    .collect(toSet());

    Function<Executable, Set<String>> methodAssociationProperties = ClientUtils.memoize(
            m -> getAnnotation(m, Association.class).map(
                    a -> {
                        Set<String> associations = Arrays.stream(a.value()).collect(toSet());
                        if (associations.isEmpty()) {
                            log.warn("@Association on handler method {} does not define a property. This is probably a mistake.", m);
                        }
                        return associations;
                    }).orElseGet(Collections::emptySet));

    @Override
    public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
        if (!handlerMatcher.canHandle(message)) {
            return Optional.empty();
        }
        var matches = repository.findByAssociation(associations(message));
        if (matches.isEmpty()) {
            return handlerMatcher.getInvoker(null, message)
                    .map(i -> new StatefulHandlerInvoker(i, null));
        }
        HandlerInvoker result = null;
        for (Entry<?> entry : matches) {
            var invoker = handlerMatcher.getInvoker(entry.getValue(), message)
                    .map(i -> new StatefulHandlerInvoker(i, entry));
            if (invoker.isPresent()) {
                result = result == null ? invoker.get() : result.combine(invoker.get());
            }
        }
        return Optional.ofNullable(result);
    }

    protected Collection<String> associations(DeserializingMessage message) {
        return Optional.ofNullable(message.getPayload()).stream()
                .flatMap(payload -> Stream.concat(handlerMatcher.matchingMethods(message)
                                                          .flatMap(e -> methodAssociationProperties.apply(e).stream()),
                                                  getAssociationProperties().stream())
                        .flatMap(property -> ReflectionUtils.readProperty(property, payload).stream()))
                .map(v -> {
                    if (v instanceof Id<?> id) {
                        return id.getFunctionalId();
                    }
                    return v.toString();
                }).collect(toSet());
    }

    @Getter
    @AllArgsConstructor
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    protected class StatefulHandlerInvoker implements HandlerInvoker {
        @Delegate(excludes = ExcludedMethods.class)
        HandlerInvoker delegate;
        Entry<?> currentEntry;

        @Override
        @SneakyThrows
        public Object invoke(BiFunction<Object, Object, Object> combiner) {
            Object result = delegate.invoke(combiner);
            if (delegate.getTargetClass().isInstance(result)) {
                if (currentEntry == null || !Objects.equals(currentEntry.getValue(), result)) {
                    repository.set(result, currentEntry == null ? computeId(result) : currentEntry.getId()).get();
                }
                return null;
            }
            if (result == null && delegate.expectResult() && delegate.getMethod() instanceof Method m
                && (delegate.getTargetClass().isAssignableFrom(m.getReturnType())
                    || m.getReturnType().isAssignableFrom(delegate.getTargetClass()))) {
                if (currentEntry != null) {
                    repository.delete(currentEntry.getId()).get();
                }
                return null;
            }
            return result;
        }

        private static Object computeId(Object handler) {
            return getAnnotatedPropertyValue(handler, EntityId.class).orElseGet(FluxCapacitor::generateId);
        }

        private interface ExcludedMethods {
            Object invoke(BiFunction<Object, Object, Object> combiner);

        }
    }
}
