package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mockito;

import java.util.Map;
import java.util.WeakHashMap;

@Slf4j
public class TestClient extends InMemoryClient {
    private static final Map<Object, Object> spiedComponents = new WeakHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T decorate(T component) {
        return (T) spiedComponents.computeIfAbsent(component, Mockito::spy);
    }
}
