package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.common.IdentityProvider;

import java.util.concurrent.atomic.AtomicInteger;

public class PredictableIdFactory implements IdentityProvider {

    private final AtomicInteger next = new AtomicInteger();

    @Override
    public String nextFunctionalId() {
        return Integer.toString(next.getAndIncrement());
    }
}
