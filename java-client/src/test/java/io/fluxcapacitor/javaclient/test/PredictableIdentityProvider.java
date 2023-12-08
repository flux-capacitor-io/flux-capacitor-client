package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.common.IdentityProvider;

import java.util.ServiceLoader;

public interface PredictableIdentityProvider extends IdentityProvider {
    static IdentityProvider defaultPredictableIdentityProvider() {
        return ServiceLoader.load(PredictableIdentityProvider.class)
            .findFirst()
            .orElseGet(PredictableIdFactory::new);
    }
}
