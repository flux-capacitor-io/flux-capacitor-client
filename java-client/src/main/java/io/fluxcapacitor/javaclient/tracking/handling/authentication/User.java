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

package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.ThrowingRunnable;
import lombok.SneakyThrows;

import java.security.Principal;
import java.util.concurrent.Callable;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public interface User extends Principal {

    ThreadLocal<User> current = new ThreadLocal<>();

    @SuppressWarnings("unchecked")
    static <U extends User> U getCurrent() {
        return (U) current.get();
    }

    @SneakyThrows
    default <T> T apply(Callable<T> f) {
        User previousUser = User.getCurrent();
        try {
            User.current.set(this);
            return f.call();
        } finally {
            User.current.set(previousUser);
        }
    }

    @SneakyThrows
    default void run(ThrowingRunnable f) {
        apply(() -> {
            f.run();
            return null;
        });
    }

    boolean hasRole(String role);
}
