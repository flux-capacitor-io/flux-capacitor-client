package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;

public abstract class SelectionTestHandler {
    @HandleEvent
    void handle(String event) {
        FluxCapacitor.publishEvent(new EventReceived(getClass(),
                                                     Tracker.current().map(Tracker::getName).orElse(null)));
    }
}
