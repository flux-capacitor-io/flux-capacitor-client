package io.fluxcapacitor.javaclient.keyvalue;

import io.fluxcapacitor.common.Awaitable;

public interface KeyValueStore {

    Awaitable store(String key, Object value);

    Awaitable get(String key);

    Awaitable delete(String key);

}
