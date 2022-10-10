package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.tracking.Consumer;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;

@Consumer(name = "ScheduledCommandHandler", typeFilter = "io.fluxcapacitor.javaclient.scheduling.ScheduledCommand")
public class ScheduledCommandHandler {
    @HandleSchedule
    void handle(ScheduledCommand schedule) {
        Object command = FluxCapacitor.get().serializer().deserialize(schedule.getCommand().getData());
        FluxCapacitor.sendAndForgetCommand(command);
    }
}
