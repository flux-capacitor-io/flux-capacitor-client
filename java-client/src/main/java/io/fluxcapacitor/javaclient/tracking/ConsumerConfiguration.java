package io.fluxcapacitor.javaclient.tracking;

import lombok.Value;

import java.util.function.Predicate;

@Value
public class ConsumerConfiguration {
    String name;
    Predicate<Object> handlerFilter;
    TrackingConfiguration trackingConfiguration;
}
