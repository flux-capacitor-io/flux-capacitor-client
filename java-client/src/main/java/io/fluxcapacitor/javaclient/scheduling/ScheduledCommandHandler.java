package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;

public class ScheduledCommandHandler {
    @HandleSchedule
    void handle(ScheduledCommand schedule) {
        Object command = FluxCapacitor.get().serializer().deserialize(schedule.getCommand().getData());
        FluxCapacitor.sendAndForgetCommand(command);
    }
}
