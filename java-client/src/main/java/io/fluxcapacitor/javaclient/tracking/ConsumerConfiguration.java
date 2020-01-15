package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

import java.util.function.Function;
import java.util.function.Predicate;

@Value
@Builder(builderClassName = "Builder", toBuilder = true)
public class ConsumerConfiguration {
    public static Function<MessageType, String> DEFAULT_CONSUMER_NAME = Enum::name;

    @NonNull MessageType messageType;
    @NonNull String name;
    @Default @Accessors(fluent = true) boolean prependApplicationName = true;
    @NonNull @Default Predicate<Object> handlerFilter = o -> true;
    @NonNull @Default TrackingConfiguration trackingConfiguration = TrackingConfiguration.DEFAULT;
    @NonNull @Default ErrorHandler errorHandler = new LoggingErrorHandler();

    public static ConsumerConfiguration getDefault(MessageType messageType) {
        return ConsumerConfiguration.builder().messageType(messageType)
                .name(DEFAULT_CONSUMER_NAME.apply(messageType)).build();
    }
}
