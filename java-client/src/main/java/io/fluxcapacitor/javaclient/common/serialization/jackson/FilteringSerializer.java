package io.fluxcapacitor.javaclient.common.serialization.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerMatcher;
import io.fluxcapacitor.javaclient.common.serialization.FilterContent;
import io.fluxcapacitor.javaclient.tracking.handling.InputParameterResolver;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

@AllArgsConstructor
@Slf4j
public class FilteringSerializer extends JsonSerializer<Object> {

    public static ObjectMapper installSerializer(ObjectMapper mapper) {
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
        return mapper;
    }

    private final Function<Class<?>, HandlerMatcher<Object, User>> matcherCache = memoize(
            type -> HandlerInspector.inspect(type, List.of(new InputParameterResolver()), FilterContent.class));
    private final JsonSerializer<Object> defaultSerializer;

    @Override
    @SneakyThrows
    public void serialize(Object input, JsonGenerator jsonGenerator, SerializerProvider provider) {
        Object value = input;
        try {
            User viewer = User.getCurrent();
            if (viewer != null && value != null) {
                Optional<HandlerInvoker> invoker = matcherCache.apply(value.getClass()).findInvoker(value, viewer);
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
        defaultSerializer.serialize(value, jsonGenerator, provider);
    }

    @Override
    public boolean isEmpty(SerializerProvider provider, Object value) {
        if (super.isEmpty(provider, value)) {
            return true;
        }
        try {
            User viewer = User.getCurrent();
            return viewer != null && matcherCache.apply(value.getClass()).findInvoker(value, viewer)
                    .filter(handlerInvoker -> handlerInvoker.invoke() == null).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }
}