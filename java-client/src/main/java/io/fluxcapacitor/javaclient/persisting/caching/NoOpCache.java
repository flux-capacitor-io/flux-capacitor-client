package io.fluxcapacitor.javaclient.persisting.caching;

import java.util.function.Function;

public enum NoOpCache implements Cache {
    INSTANCE;

    @Override
    public void put(String id, Object value) {
        //no op
    }

    @Override
    public <T> T get(String id, Function<? super String, T> mappingFunction) {
        return mappingFunction.apply(id);
    }

    @Override
    public <T> T getIfPresent(String id) {
        return null;
    }

    @Override
    public void invalidate(String id) {
        //no op
    }

    @Override
    public void invalidateAll() {
        //no op
    }
}
