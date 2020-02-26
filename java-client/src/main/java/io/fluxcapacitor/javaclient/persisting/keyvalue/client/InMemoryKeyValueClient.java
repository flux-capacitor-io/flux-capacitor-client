package io.fluxcapacitor.javaclient.persisting.keyvalue.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Data;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    public Awaitable putValue(String key, Data<byte[]> value, Guarantee guarantee) {
        values.put(key, value);
        return Awaitable.ready();
    }

    @Override
    public CompletableFuture<Boolean> putValueIfAbsent(String key, Data<byte[]> value) {
        return CompletableFuture.completedFuture(values.putIfAbsent(key, value) == null);
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

    @Override
    public void close() {
        //no op
    }
}
