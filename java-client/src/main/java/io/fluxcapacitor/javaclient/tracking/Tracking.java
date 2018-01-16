package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.Registration;

import java.util.Arrays;
import java.util.List;

public interface Tracking {

    default Registration start(Object... handlers) {
        return start(Arrays.asList(handlers));
    }

    Registration start(List<Object> handlers);

}
