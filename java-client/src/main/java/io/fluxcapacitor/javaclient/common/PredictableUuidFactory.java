package io.fluxcapacitor.javaclient.common;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class PredictableUuidFactory implements IdentityProvider {

    private final AtomicInteger next = new AtomicInteger();

    @Override
    public String nextFunctionalId() {
        return UUID.nameUUIDFromBytes((next.getAndIncrement() + "").getBytes()).toString();
    }
}
