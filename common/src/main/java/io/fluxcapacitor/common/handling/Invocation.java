package io.fluxcapacitor.common.handling;

import io.fluxcapacitor.common.Registration;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

@Value
public class Invocation {
    private static final ThreadLocal<Invocation> current = new ThreadLocal<>();
    @Getter(lazy = true)
    String id = UUID.randomUUID().toString();
    transient List<BiConsumer<Object, Throwable>> callbacks = new ArrayList<>();
    public Invocation previous;

    @SneakyThrows
    public static <V> V performInvocation(Callable<V> callable) {
        Invocation previousInvocation = current.get();
        Invocation invocation = new Invocation(previousInvocation);
        current.set(invocation);
        try {
            V result = callable.call();
            invocation.getCallbacks().forEach(c -> c.accept(result, null));
            return result;
        } catch (Throwable e) {
            invocation.getCallbacks().forEach(c -> c.accept(null, e));
            throw e;
        } finally {
            current.set(previousInvocation);
        }
    }

    public static Invocation getCurrent() {
        return current.get();
    }

    public static Registration whenHandlerCompletes(BiConsumer<Object, Throwable> callback) {
        return whenHandlerCompletes(callback, true);
    }

    public static Registration whenHandlerCompletes(BiConsumer<Object, Throwable> callback, boolean outer) {
        Invocation invocation = current.get();
        if (invocation == null) {
            callback.accept(null, null);
            return Registration.noOp();
        } else {
            return invocation.registerCallback(callback, outer);
        }
    }

    private Registration registerCallback(BiConsumer<Object, Throwable> callback, boolean outer) {
        if (outer && previous != null) {
            return previous.registerCallback(callback, true);
        }
        callbacks.add(callback);
        return () -> callbacks.remove(callback);
    }
}
