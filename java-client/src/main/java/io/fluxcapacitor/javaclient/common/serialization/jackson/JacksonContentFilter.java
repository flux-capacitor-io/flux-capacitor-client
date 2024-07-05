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

@Slf4j
public class JacksonContentFilter implements ContentFilter {

    private final ObjectMapper mapper;

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
            }
            followUp.accept(value);
        }

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
