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
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.Entry;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.modeling.HandlerRepository;
import io.fluxcapacitor.javaclient.modeling.Id;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperties;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotation;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getPropertyName;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

@Getter
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class StatefulHandler implements Handler<DeserializingMessage> {
    Class<?> targetClass;
    HandlerMatcher<Object, DeserializingMessage> handlerMatcher;
    HandlerRepository repository;

    @Getter(lazy = true)
    Map<String, AssociationValue> associationProperties =
            getAnnotatedProperties(getTargetClass(), Association.class).stream()
                    .flatMap(member -> getAnnotation(member, Association.class).stream().flatMap(
                            association -> {
                                String propertyName = getPropertyName(member);
                                return (association.value().length > 0
                                        ? Arrays.stream(association.value()) : Stream.of(propertyName))
                                        .map(v -> {
                                            var associationValue = AssociationValue.valueOf(association);
                                            if (associationValue.getPath().isBlank()) {
                                                associationValue = associationValue.toBuilder()
                                                        .path(propertyName).build();
                                            }
                                            return Map.entry(v, associationValue);
                                        });
                            }))
                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

    Function<Executable, Map<String, AssociationValue>> methodAssociationProperties = ClientUtils.memoize(
            m -> getAnnotation(m, Association.class).map(
                    association -> {
                        Map<String, AssociationValue> associations = Arrays.stream(association.value()).collect(
                                toMap(Function.identity(), v -> AssociationValue.valueOf(association), (a, b) -> a));
                        if (associations.isEmpty()) {
                            log.warn("@Association on {} does not define a property. This is probably a mistake.", m);
                        }
                        return associations;
                    }).orElseGet(Collections::emptyMap));

    Function<Executable, Boolean> alwaysAssociateMethods = ClientUtils.memoize(
            m -> getAnnotation(m, Association.class).filter(Association::always).isPresent());

    @Override
    public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
        if (!handlerMatcher.canHandle(message)) {
            return Optional.empty();
        }
        var alwaysMatch = handlerMatcher.matchingMethods(message).anyMatch(alwaysAssociateMethods::apply);
        var alwaysInvoke = alwaysMatch && handlerMatcher.matchingMethods(message).allMatch(ReflectionUtils::isStatic);
        var matches = alwaysInvoke ? List.<Entry<?>>of() : alwaysMatch ? repository.getAll()
                : repository.findByAssociation(associations(message));
        if (matches.isEmpty()) {
            return handlerMatcher.getInvoker(null, message)
                    .filter(i -> {
                        if (alreadyFiltered(i)) {
                            return true;
                        }
                        String routingKey = ReflectionUtils.getAnnotatedProperty(targetClass, EntityId.class)
                                .map(ReflectionUtils::getPropertyName)
                                .flatMap(message::getRoutingKey)
                                .orElseGet(message::getMessageId);
                        return canTrackerHandle(message, routingKey);
                    })
                    .map(i -> new StatefulHandlerInvoker(i, null, message));
        }
        List<HandlerInvoker> invokers = new ArrayList<>();
        for (Entry<?> entry : matches) {
            handlerMatcher.getInvoker(entry.getValue(), message)
                    .filter(i -> alreadyFiltered(i) || canTrackerHandle(message, entry.getId()))
                    .map(i -> new StatefulHandlerInvoker(i, entry, message))
                    .ifPresent(invokers::add);
        }
        return HandlerInvoker.join(invokers);
    }

    protected boolean alreadyFiltered(HandlerInvoker i) {
        return ReflectionUtils.getMethodAnnotation(i.getMethod(), RoutingKey.class).isPresent();
    }

    protected Boolean canTrackerHandle(DeserializingMessage message, String routingKey) {
        return Tracker.current().filter(tracker -> tracker.getConfiguration().ignoreSegment())
                .map(tracker -> tracker.canHandle(message, routingKey)).orElse(true);
    }

    protected Map<String, String> associations(DeserializingMessage message) {
        return ofNullable(message.getPayload()).stream()
                .flatMap(payload -> Stream.concat(
                                handlerMatcher.matchingMethods(message)
                                        .flatMap(e -> methodAssociationProperties.apply(e).entrySet().stream()),
                                getAssociationProperties().entrySet().stream())
                        .filter(entry -> includedPayload(payload, entry.getValue()))
                        .flatMap(entry -> ReflectionUtils.readProperty(entry.getKey(), payload)
                                .or(() -> entry.getValue().isExcludeMetadata() ? empty() : ofNullable(message.getMetadata().get(entry.getKey())))
                                .map(v -> {
                                    if (v instanceof Id<?> id) {
                                        return id.getFunctionalId();
                                    }
                                    return v.toString();
                                })
                                .map(v -> Map.entry(v, entry.getValue().getPath()))
                                .stream()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a.isBlank() ? b : a));
    }

    protected boolean includedPayload(Object payload, AssociationValue association) {
        Class<?> payloadType = payload.getClass();
        if (!association.includedClasses.isEmpty()
            && association.includedClasses.stream().noneMatch(c -> c.isAssignableFrom(payloadType))) {
            return false;
        }
        return association.excludedClasses.stream().noneMatch(c -> c.isAssignableFrom(payloadType));
    }

    @Override
    public String toString() {
        return "StatefulHandler[%s]".formatted(targetClass);
    }

    @Value
    @Builder(toBuilder = true)
    protected static class AssociationValue {
        public static AssociationValue valueOf(Association association) {
            return ReflectionUtils.convertAnnotation(association, AssociationValue.class);
        }

        List<String> value;
        String path;
        List<Class<?>> includedClasses;
        List<Class<?>> excludedClasses;
        boolean excludeMetadata;
        boolean always;

        public String getPath() {
            return path == null ? "" : path;
        }
    }

    protected class StatefulHandlerInvoker extends HandlerInvoker.DelegatingHandlerInvoker {
        private final Entry<?> currentEntry;
        private final DeserializingMessage message;

        public StatefulHandlerInvoker(HandlerInvoker delegate, Entry<?> currentEntry, DeserializingMessage message) {
            super(delegate);
            this.currentEntry = currentEntry;
            this.message = message;
        }

        @Override
        public Object invoke(BiFunction<Object, Object, Object> combiner) {
            Object result = delegate.invoke(combiner);
            handleResult(result);
            return result;
        }

        @SneakyThrows
        protected void handleResult(Object result) {
            if (result instanceof Collection<?> collection) {
                collection.forEach(this::handleResult);
            } else if (getTargetClass().isInstance(result)) {
                if (currentEntry == null || !Objects.equals(currentEntry.getValue(), result)) {
                    repository.set(result, computeId(result)).get();
                }
            } else if (result == null && expectResult() && getMethod() instanceof Method m
                       && (getTargetClass().isAssignableFrom(m.getReturnType())
                           || m.getReturnType().isAssignableFrom(getTargetClass()))) {
                if (currentEntry != null) {
                    repository.delete(currentEntry.getId()).get();
                }
            }
        }

        protected Object computeId(Object handler) {
            return getAnnotatedPropertyValue(handler, EntityId.class)
                    .or(() -> ofNullable(currentEntry).map(Entry::getId))
                    .orElseGet(message::getMessageId);
        }
    }
}
