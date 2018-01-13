package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;

public interface Tracker {

    Registration start(TrackingService trackingService, FluxCapacitor fluxCapacitor);

}
