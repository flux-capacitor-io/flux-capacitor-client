package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DefaultEventGateway implements EventGateway {
    private final PublicationGateway delegate;

    @Override
    public void publish(Object event) {
        delegate.sendAndForget(event);
    }

    @Override
    public void publish(Object payload, Metadata metadata) {
        delegate.sendAndForget(payload, metadata);
    }
}
