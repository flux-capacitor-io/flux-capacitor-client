package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.Value;

import java.util.function.Function;
import java.util.function.Predicate;

@Value
@Builder(builderClassName = "Builder", toBuilder = true)
public class ConsumerConfiguration {
    public static Function<MessageType, String> DEFAULT_CONSUMER_NAME = Enum::name;

    String name;
    @Default @Getter(AccessLevel.NONE)
    boolean prependApplicationName = true;
    @Default
    Predicate<Object> handlerFilter = o -> true;
    @Default
    TrackingConfiguration trackingConfiguration = TrackingConfiguration.DEFAULT;
    @Default
    ErrorHandler errorHandler = new LoggingErrorHandler();

    public static ConsumerConfiguration getDefault(MessageType messageType) {
        return ConsumerConfiguration.builder().name(DEFAULT_CONSUMER_NAME.apply(messageType)).build();
    }

    public boolean prependApplicationName() {
        return prependApplicationName;
    }
}
