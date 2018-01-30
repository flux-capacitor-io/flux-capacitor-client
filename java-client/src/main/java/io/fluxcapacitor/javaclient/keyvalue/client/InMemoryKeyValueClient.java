package io.fluxcapacitor.javaclient.keyvalue.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryKeyValueClient implements KeyValueClient {

    private final Map<String, Data<byte[]>> values;

    public InMemoryKeyValueClient() {
        this(new ConcurrentHashMap<>());
    }

    protected InMemoryKeyValueClient(Map<String, Data<byte[]>> map) {
        values = map;
    }

    @Override
    public Awaitable putValue(String key, Data<byte[]> value) {
        values.put(key, value);
        return Awaitable.ready();
    }

    @Override
    public Data<byte[]> getValue(String key) {
        return values.get(key);
    }

    @Override
    public Awaitable deleteValue(String key) {
        values.remove(key);
        return Awaitable.ready();
    }
}
