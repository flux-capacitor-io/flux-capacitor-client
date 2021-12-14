package io.fluxcapacitor.spring;

import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;

@FunctionalInterface
public interface FluxCapacitorBuilderCustomizer {
    FluxCapacitorBuilder customize(FluxCapacitorBuilder builder);
}
