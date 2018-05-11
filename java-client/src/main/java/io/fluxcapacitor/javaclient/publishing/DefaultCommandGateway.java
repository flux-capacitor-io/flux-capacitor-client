package io.fluxcapacitor.javaclient.publishing;

import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

@AllArgsConstructor
public class DefaultCommandGateway implements CommandGateway {
    @Delegate
    private final GenericGateway delegate;
}
