/*
 * Copyright (c) 2016-2021 Flux Capacitor.
 *
 * Do not copy, cite or distribute without permission.
 */

package io.fluxcapacitor.testserver.endpoints.metrics;

import io.fluxcapacitor.common.api.ClientEvent;
import io.fluxcapacitor.common.api.Metadata;

public interface MetricsLog {

    default void registerMetrics(ClientEvent event) {
        registerMetrics(event, Metadata.empty());
    }

    void registerMetrics(ClientEvent event, Metadata metadata);

}
