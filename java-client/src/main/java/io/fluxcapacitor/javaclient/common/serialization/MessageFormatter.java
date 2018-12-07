package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;

import java.util.function.Function;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static java.lang.String.format;

@FunctionalInterface
public interface MessageFormatter extends Function<DeserializingMessage, String> {
    MessageFormatter DEFAULT = m -> m.isDeserialized() ? getAnnotatedPropertyValue(m.getPayload(), RoutingKey.class)
            .map(key -> format("%s (routing key: %s)", m.getPayloadClass().getSimpleName(), key))
            .orElse(m.getPayloadClass().getSimpleName()) : m.getType();
}
