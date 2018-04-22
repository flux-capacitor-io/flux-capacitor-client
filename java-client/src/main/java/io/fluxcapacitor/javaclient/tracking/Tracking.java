package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;

import java.util.Arrays;
import java.util.List;

public interface Tracking {

    default Registration start(FluxCapacitor fluxCapacitor, Object... handlers) {
        return start(fluxCapacitor, Arrays.asList(handlers));
    }

    Registration start(FluxCapacitor fluxCapacitor, List<?> handlers);

}
