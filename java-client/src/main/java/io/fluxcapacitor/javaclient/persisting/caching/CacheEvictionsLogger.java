package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.publishing.MetricsGateway;
import lombok.AllArgsConstructor;

import java.util.function.Consumer;

@AllArgsConstructor
public class CacheEvictionsLogger implements Consumer<CacheEvictionEvent> {

    private final MetricsGateway metricsGateway;

    public Registration register(Cache cache) {
        return cache.registerEvictionListener(this);
    }

    @Override
    public void accept(CacheEvictionEvent evictionEvent) {
        metricsGateway.publish(evictionEvent);
    }
}
