package io.fluxcapacitor.javaclient.common;

import java.util.UUID;

public class UuidFactory implements IdentityProvider {
    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
