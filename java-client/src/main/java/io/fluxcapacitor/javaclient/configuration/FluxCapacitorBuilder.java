package io.fluxcapacitor.javaclient.configuration;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.eventsourcing.EventStoreClient;
import io.fluxcapacitor.javaclient.gateway.GatewayClient;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueClient;
import io.fluxcapacitor.javaclient.scheduling.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.TrackingConfiguration;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface FluxCapacitorBuilder {

    FluxCapacitorBuilder withSerializer(Serializer serializer);

    FluxCapacitorBuilder disableMessageCorrelation();

    FluxCapacitorBuilder disableCommandValidation();

    FluxCapacitorBuilder disableResultTracking();

    FluxCapacitorBuilder configureDefaultConsumer(MessageType messageType, TrackingConfiguration trackingConfiguration);

    FluxCapacitorBuilder configureDefaultConsumer(MessageType messageType, String name,
                                                  TrackingConfiguration trackingConfiguration);

    FluxCapacitorBuilder configureConsumer(MessageType messageType, Predicate<Object> handlerFilter, String name,
                                           TrackingConfiguration trackingConfiguration);

    FluxCapacitorBuilder withGatewayClient(Function<MessageType, ? extends GatewayClient> factory);

    FluxCapacitorBuilder withTrackingClient(Function<MessageType, ? extends TrackingClient> factory);

    FluxCapacitorBuilder withSchedulingClient(Supplier<? extends SchedulingClient> factory);

    FluxCapacitorBuilder withKeyValueClient(Supplier<? extends KeyValueClient> factory);

    FluxCapacitorBuilder withEventStoreClient(Supplier<? extends EventStoreClient> factory);

    FluxCapacitor build();

}
