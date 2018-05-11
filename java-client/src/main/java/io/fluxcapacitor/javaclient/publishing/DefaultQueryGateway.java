package io.fluxcapacitor.javaclient.publishing;

import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

@AllArgsConstructor
public class DefaultQueryGateway implements QueryGateway {
    @Delegate
    private final RequestGateway delegate;
}
