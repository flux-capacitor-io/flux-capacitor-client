/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.common.IdentityProvider;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

@Value
public class Invocation {

    private static final ThreadLocal<Invocation> current = new ThreadLocal<>();
    @Getter(lazy = true) String id = IdentityProvider.defaultIdentityProvider.nextTechnicalId();
    transient List<BiConsumer<Object, Throwable>> callbacks = new ArrayList<>();

    @SneakyThrows
    public static <V> V performInvocation(Callable<V> callable) {
        if (current.get() != null) {
            return callable.call();
        }
        Invocation invocation = new Invocation();
        current.set(invocation);
        try {
            V result = callable.call();
            invocation.getCallbacks().forEach(c -> c.accept(result, null));
            return result;
        } catch (Throwable e) {
            invocation.getCallbacks().forEach(c -> c.accept(null, e));
            throw e;
        } finally {
            current.remove();
        }
    }

    public static Invocation getCurrent() {
        return current.get();
    }

    public static Registration whenHandlerCompletes(BiConsumer<Object, Throwable> callback) {
        Invocation invocation = current.get();
        if (invocation == null) {
            callback.accept(null, null);
            return Registration.noOp();
        } else {
            return invocation.registerCallback(callback);
        }
    }

    private Registration registerCallback(BiConsumer<Object, Throwable> callback) {
        callbacks.add(callback);
        return () -> callbacks.remove(callback);
    }
}
