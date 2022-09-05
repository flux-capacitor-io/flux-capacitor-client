package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.CommandGateway;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.client.DefaultTracker;

import java.util.List;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.SCHEDULE;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.handleBatch;

public class ScheduledCommandHandler {
    public static Registration start(FluxCapacitor fluxCapacitor) {
        return new ScheduledCommandHandler(fluxCapacitor).registration;
    }

    private final Serializer serializer;
    private final CommandGateway commandGateway;
    private final Registration registration;

    protected ScheduledCommandHandler(FluxCapacitor fluxCapacitor) {
        this.serializer = fluxCapacitor.serializer();
        this.commandGateway = fluxCapacitor.commandGateway();
        this.registration = DefaultTracker.start(this::handleSchedules, ConsumerConfiguration.builder()
                .messageType(SCHEDULE).typeFilter(ScheduledCommand.class.getName())
                .name(ScheduledCommandHandler.class.getSimpleName()).build(), fluxCapacitor);
        fluxCapacitor.beforeShutdown(registration::cancel);
    }

    protected void handleSchedules(List<SerializedMessage> schedules) {
        handleBatch(serializer.deserializeMessages(schedules.stream(), SCHEDULE))
                .forEach(s -> {
                    SerializedMessage serializedCommand = s.<ScheduledCommand>getPayload().getCommand();
                    serializer.deserializeMessages(Stream.of(serializedCommand), COMMAND).findFirst().map(
                            DeserializingMessage::toMessage).ifPresent(commandGateway::sendAndForget);
                });
    }
}
