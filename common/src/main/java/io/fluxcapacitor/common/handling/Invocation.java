package io.fluxcapacitor.common.handling;

import io.fluxcapacitor.common.Registration;
import lombok.Getter;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

@Value
public class Invocation {
    @Getter(lazy = true)
    String id = UUID.randomUUID().toString();
    List<BiConsumer<Object, Throwable>> callbacks = new ArrayList<>();

    public Registration registerCallback(BiConsumer<Object, Throwable> callback) {
        callbacks.add(callback);
        return () -> callbacks.remove(callback);
    }
}
