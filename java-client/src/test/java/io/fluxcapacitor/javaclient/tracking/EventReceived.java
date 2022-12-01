package io.fluxcapacitor.javaclient.tracking;

import lombok.Value;

@Value
public class EventReceived {
    Class<?> handler;
    String consumerName;
}
