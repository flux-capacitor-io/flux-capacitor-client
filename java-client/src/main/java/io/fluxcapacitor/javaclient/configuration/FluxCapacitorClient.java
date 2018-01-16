package io.fluxcapacitor.javaclient.configuration;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.connection.ApplicationProperties;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.eventsourcing.EventStoreClient;
import io.fluxcapacitor.javaclient.eventsourcing.InMemoryEventStoreClient;
import io.fluxcapacitor.javaclient.eventsourcing.websocket.WebSocketEventStoreClient;
import io.fluxcapacitor.javaclient.gateway.CommandGateway;
import io.fluxcapacitor.javaclient.gateway.GatewayClient;
import io.fluxcapacitor.javaclient.gateway.QueryGateway;
import io.fluxcapacitor.javaclient.gateway.ResultGateway;
import io.fluxcapacitor.javaclient.gateway.websocket.WebsocketGatewayClient;
import io.fluxcapacitor.javaclient.keyvalue.InMemoryKeyValueClient;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueClient;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.keyvalue.websocket.WebsocketKeyValueClient;
import io.fluxcapacitor.javaclient.scheduling.InMemorySchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.scheduling.SchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.websocket.WebsocketSchedulingClient;
import io.fluxcapacitor.javaclient.tracking.InMemoryMessageStore;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import io.fluxcapacitor.javaclient.tracking.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.TrackingConfiguration;
import io.fluxcapacitor.javaclient.tracking.websocket.WebsocketTrackingClient;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.javaclient.common.connection.ServiceUrlBuilder.*;

@AllArgsConstructor
public class FluxCapacitorClient implements FluxCapacitor {

    private final Supplier<EventStore> eventStoreSupplier;
    private final Supplier<Scheduler> schedulerSupplier;
    private final Supplier<KeyValueStore> keyValueStoreSupplier;
    private final Supplier<CommandGateway> commandGatewaySupplier;
    private final Supplier<QueryGateway> queryGatewaySupplier;
    private final Supplier<ResultGateway> resultGatewaySupplier;
    private final Function<MessageType, Tracking> trackingSupplier;

    public static FluxCapacitorBuilder usingInMemory() {
        Map<MessageType, InMemoryMessageStore> messageStores = new HashMap<>();
        InMemorySchedulingClient schedulingClient = new InMemorySchedulingClient();
        InMemoryEventStoreClient eventStoreClient = new InMemoryEventStoreClient();
        InMemoryKeyValueClient keyValueClient = new InMemoryKeyValueClient();
        Function<MessageType, InMemoryMessageStore> messageStoreFactory = type -> messageStores.computeIfAbsent(
                type, t -> {
                    switch (t) {
                        case EVENT:
                            return eventStoreClient;
                        case SCHEDULE:
                            return schedulingClient;
                        default:
                            return new InMemoryMessageStore();
                    }
                });
        return new Builder().withGatewayClient(messageStoreFactory).withTrackingClient(messageStoreFactory)
                .withEventStoreClient(() -> eventStoreClient).withSchedulingClient(() -> schedulingClient)
                .withKeyValueClient(() -> keyValueClient);
    }

    public static FluxCapacitorBuilder usingWebSockets(ApplicationProperties properties) {
        Map<MessageType, GatewayClient> gatewayClients = new ConcurrentHashMap<>();
        Map<MessageType, TrackingClient> trackingClients = new ConcurrentHashMap<>();
        Supplier<KeyValueClient> keyValueClient = memoize(() -> new WebsocketKeyValueClient(keyValueUrl(properties)));
        return new Builder()
                .withGatewayClient(t -> gatewayClients
                        .computeIfAbsent(t, type -> new WebsocketGatewayClient(producerUrl(t, properties))))
                .withTrackingClient(t -> trackingClients
                        .computeIfAbsent(t, type -> new WebsocketTrackingClient(consumerUrl(t, properties))))
                .withEventStoreClient(
                        () -> new WebSocketEventStoreClient(eventSourcingUrl(properties), keyValueClient.get()))
                .withSchedulingClient(() -> new WebsocketSchedulingClient(schedulingUrl(properties)))
                .withKeyValueClient(keyValueClient);
    }

    @Override
    public CommandGateway commandGateway() {
        return commandGatewaySupplier.get();
    }

    @Override
    public QueryGateway queryGateway() {
        return queryGatewaySupplier.get();
    }

    @Override
    public ResultGateway resultGateway() {
        return resultGatewaySupplier.get();
    }

    @Override
    public EventStore eventStore() {
        return eventStoreSupplier.get();
    }

    @Override
    public Scheduler scheduler() {
        return schedulerSupplier.get();
    }

    @Override
    public KeyValueStore keyValueStore() {
        return keyValueStoreSupplier.get();
    }

    @Override
    public Tracking tracking(MessageType messageType) {
        return trackingSupplier.apply(messageType);
    }

    public static class Builder implements FluxCapacitorBuilder {

        private Serializer serializer = new JacksonSerializer();
        private boolean disableMessageCorrelation;
        private boolean disableCommandValidation;
//        private final Map<MessageType, List<ConsumerConfiguration>> consumerConfigurations;


        @Override
        public FluxCapacitorBuilder withSerializer(Serializer serializer) {
            this.serializer = serializer;
            return this;
        }

        @Override
        public FluxCapacitorBuilder disableMessageCorrelation() {
            return this;
        }

        @Override
        public FluxCapacitorBuilder disableCommandValidation() {
            return this;
        }

        @Override
        public FluxCapacitorBuilder disableResultTracking() {
            return this;
        }

        @Override
        public FluxCapacitorBuilder configureDefaultConsumer(MessageType messageType,
                                                             TrackingConfiguration trackingConfiguration) {
            return this;
        }

        @Override
        public FluxCapacitorBuilder configureDefaultConsumer(MessageType messageType, String name,
                                                             TrackingConfiguration trackingConfiguration) {
            return this;
        }

        @Override
        public FluxCapacitorBuilder configureConsumer(MessageType messageType, Predicate<Object> handlerFilter,
                                                      String name, TrackingConfiguration trackingConfiguration) {
            return this;
        }

        @Override
        public FluxCapacitorBuilder withGatewayClient(Function<MessageType, ? extends GatewayClient> factory) {
            return this;
        }

        @Override
        public FluxCapacitorBuilder withTrackingClient(Function<MessageType, ? extends TrackingClient> factory) {
            return this;
        }

        @Override
        public FluxCapacitorBuilder withSchedulingClient(Supplier<? extends SchedulingClient> factory) {
            return this;
        }

        @Override
        public FluxCapacitorBuilder withKeyValueClient(Supplier<? extends KeyValueClient> factory) {
            return this;
        }

        @Override
        public FluxCapacitorBuilder withEventStoreClient(Supplier<? extends EventStoreClient> factory) {
            return this;
        }

        @Override
        public FluxCapacitor build() {
            return null; //todo and make sure to memoize
        }

        @Value
        private static class ConsumerConfiguration {
            @Wither
            String name;
            Predicate<Object> handlerFilter;
            TrackingConfiguration trackingConfiguration;

            public static ConsumerConfiguration defaultForType(MessageType messageType) {
                return new ConsumerConfiguration(messageType.name(), o -> true, TrackingConfiguration.DEFAULT);
            }
        }
    }

}
