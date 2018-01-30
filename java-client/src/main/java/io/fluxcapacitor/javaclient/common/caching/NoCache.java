package io.fluxcapacitor.javaclient.common.caching;

import java.util.function.Function;

public enum NoCache implements Cache {
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
    public void invalidate(String id) {
        //no op
    }

    @Override
    public void invalidateAll() {
        //no op
    }
}
