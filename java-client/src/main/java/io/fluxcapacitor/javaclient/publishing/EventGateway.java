package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;

public interface EventGateway {

    void publish(Object event);

    void publish(Object payload, Metadata metadata);

    Registration registerLocalHandler(Object handler);

}
