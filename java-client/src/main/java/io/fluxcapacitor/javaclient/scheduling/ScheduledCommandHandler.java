package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.tracking.Consumer;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;

import static io.fluxcapacitor.common.Guarantee.NONE;

@Consumer(name = "ScheduledCommandHandler", typeFilter = "io.fluxcapacitor.javaclient.scheduling.ScheduledCommand")
public class ScheduledCommandHandler {
    @HandleSchedule
    void handle(ScheduledCommand schedule) {
        FluxCapacitor.get().client().getGatewayClient(MessageType.COMMAND).send(NONE, schedule.getCommand());
    }
}
