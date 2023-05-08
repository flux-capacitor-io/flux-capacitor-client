/*
 * Copyright (c) 2016-2021 Flux Capacitor.
 *
 * Do not copy, cite or distribute without permission.
 */

package io.fluxcapacitor.testserver.endpoints.metrics;

import io.fluxcapacitor.common.api.ClientEvent;
import io.fluxcapacitor.common.api.Metadata;

public class NoOpMetricsLog implements MetricsLog {
    @Override
    public void registerMetrics(ClientEvent event, Metadata metadata) {
        //no op
    }
}
