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

package io.fluxcapacitor.javaclient.common.serialization.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import io.fluxcapacitor.common.ThrowingConsumer;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerMatcher;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.common.serialization.ContentFilter;
import io.fluxcapacitor.javaclient.common.serialization.FilterContent;
import io.fluxcapacitor.javaclient.tracking.handling.InputParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.CurrentUserParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

/**
 * A {@link ContentFilter} implementation that uses Jackson to filter content dynamically for a specific {@link User}.
 * <p>
 * This class enables context-aware filtering based on {@link FilterContent} annotated handler methods in the value’s
 * class. These handlers can compute and return a filtered version of an object based on the current user context.
 * <p>
 * Filtering is performed via Jackson serialization, where a custom serializer is installed to intercept object
 * serialization and invoke the appropriate content filtering logic.
 *
 * <h2>How it works</h2>
 * <ul>
 *   <li>A {@link FilteringSerializer} is registered with Jackson using a {@link BeanSerializerModifier}.</li>
 *   <li>The serializer tries to invoke a {@link FilterContent}-annotated method using the {@link HandlerInspector}
 *       and passes in the root object and current {@link User} (via parameter resolvers).</li>
 *   <li>If filtering returns a different value, that value is serialized instead.</li>
 * </ul>
 *
 * @see FilterContent
 * @see User
 * @see HandlerMatcher
 */
@Slf4j
public class JacksonContentFilter implements ContentFilter {

    private final ObjectMapper mapper;

    /**
     * Creates a new content filter using the provided {@link ObjectMapper}.
     * <p>
     * The mapper will be configured with:
     * <ul>
     *     <li>ALWAYS inclusion policy (to serialize nulls)</li>
     *     <li>A {@link FilteringSerializer} for applying {@link FilterContent} annotations</li>
     *     <li>Disabled {@link JsonIgnore} handling, to ensure all fields are considered for filtering</li>
     * </ul>
     *
     * @param mapper an ObjectMapper used for filtering and serialization
     */
    public JacksonContentFilter(ObjectMapper mapper) {
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        mapper.registerModule(new SimpleModule() {
            @Override
            public void setupModule(SetupContext context) {
                super.setupModule(context);
                context.addBeanSerializerModifier(new BeanSerializerModifier() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public JsonSerializer<?> modifySerializer(
                            SerializationConfig config, BeanDescription desc, JsonSerializer<?> serializer) {
                        return new FilteringSerializer((JsonSerializer<Object>) serializer);
                    }

                });
            }
        });
        JsonUtils.disableJsonIgnore(mapper);
        this.mapper = mapper;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T filterContent(T value, User viewer) {
        if (value == null) {
            return null;
        }
        try {
            FilteringSerializer.rootValue.set(value);
            return viewer.apply(() -> mapper.convertValue(value, (Class<T>) value.getClass()));
        } catch (Exception e) {
            log.warn("Failed to filter content (type {}) for viewer {}", value.getClass(), viewer, e);
            return value;
        } finally {
            FilteringSerializer.rootValue.remove();
        }
    }

    /**
     * Custom Jackson serializer that attempts to invoke a {@link FilterContent} handler method during serialization.
     * <p>
     * It caches matchers by class and uses {@link HandlerInspector} to find the appropriate handler methods.
     * <p>
     * The serializer behaves gracefully:
     * <ul>
     *   <li>If the handler returns {@code null} and the object is not part of an array, {@code null} is written out.</li>
     *   <li>If filtering fails, the original object is serialized as a fallback.</li>
     * </ul>
     *
     * The root object (used for matching context) is tracked using a thread-local field {@link #rootValue}.
     */
    @AllArgsConstructor
    @Slf4j
    protected static class FilteringSerializer extends JsonSerializer<Object> {

        protected static final ThreadLocal<Object> rootValue = new ThreadLocal<>();

        private final Function<Class<?>, HandlerMatcher<Object, Object>> matcherCache = memoize(
                type -> HandlerInspector.inspect(type, List.of(new CurrentUserParameterResolver(),
                                                               new InputParameterResolver()), FilterContent.class));
        private final JsonSerializer<Object> defaultSerializer;

        @Override
        @SneakyThrows
        public void serialize(Object input, JsonGenerator jsonGenerator, SerializerProvider provider) {
            serializeAndThen(input, jsonGenerator, value -> defaultSerializer.serialize(
                    value, jsonGenerator, provider));
        }

        @Override
        public void serializeWithType(Object input, JsonGenerator jsonGenerator, SerializerProvider provider,
                                      TypeSerializer typeSerializer) {
            serializeAndThen(input, jsonGenerator, value -> defaultSerializer.serializeWithType(
                    value, jsonGenerator, provider, typeSerializer));
        }

        /**
         * Invokes the content filter if available and serializes the filtered result.
         * If filtering fails, it logs a warning and falls back to serializing the original object.
         *
         * @param input           the object to serialize
         * @param jsonGenerator   the JSON generator
         * @param followUp        logic to continue serialization with the possibly filtered result
         */
        @SneakyThrows
        public void serializeAndThen(Object input, JsonGenerator jsonGenerator, ThrowingConsumer<Object> followUp) {
            Object value = input;
            try {
                if (value != null) {
                    Optional<HandlerInvoker> invoker = matcherCache.apply(value.getClass()).getInvoker(value, rootValue.get());
                    if (invoker.isPresent()) {
                        value = invoker.get().invoke();
                        if (value == null) {
                            if (!jsonGenerator.getOutputContext().inArray()) {
                                jsonGenerator.writeNull();
                            }
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to filter content (type {}) for viewer {}", input.getClass(), User.getCurrent(), e);
                throw e;
            }
            followUp.accept(value);
        }

        /**
         * Determines if the value should be considered empty, based on whether filtering returns null.
         */
        @Override
        public boolean isEmpty(SerializerProvider provider, Object value) {
            if (super.isEmpty(provider, value)) {
                return true;
            }
            try {
                return matcherCache.apply(value.getClass()).getInvoker(value, rootValue.get())
                        .filter(handlerInvoker -> handlerInvoker.invoke() == null).isPresent();
            } catch (Exception ignored) {
                return false;
            }
        }
    }
}
