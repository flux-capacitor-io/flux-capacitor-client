package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.tracking.TrackingStrategy;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.Accessors;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
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
    @NonNull @Default ErrorHandler errorHandler = new LoggingErrorHandler();

    //tracking config
    @Default
    int threads = 1;
    @Default
    String typeFilter = null;
    @Default
    int maxFetchBatchSize = 1024;
    @Default
    int maxConsumerBatchSize = 1024;
    @Default
    @NonNull Duration maxWaitDuration = Duration.ofSeconds(60);
    @Default
    Duration retryDelay = Duration.ofSeconds(1);
    @Singular
    List<BatchInterceptor> batchInterceptors;
    @Default
    @Accessors(fluent = true)
    boolean ignoreMessageTarget = false;
    @Default
    TrackingStrategy readStrategy = TrackingStrategy.NEW;
    @Default
    Function<Client, String> trackerIdFactory = client -> String.format("%s_%s", client.id(), UUID.randomUUID());
    @Default
    Duration purgeDelay = null;

    public static ConsumerConfiguration getDefault(MessageType messageType) {
        return ConsumerConfiguration.builder().messageType(messageType)
                .name(DEFAULT_CONSUMER_NAME.apply(messageType)).build();
    }
}
