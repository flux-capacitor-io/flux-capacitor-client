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

/**
 * Represents an authenticated or system-level user identity within the Flux platform.
 * <p>
 * A {@code User} provides role-based access information. User instances are propagated through message metadata and can
 * be made available to business logic via {@link User#getCurrent()} or injection into handler methods.
 * <p>
 * To execute logic with a specific user bound to the current thread context, use {@link #apply(Callable)} or
 * {@link #run(ThrowingRunnable)}.
 *
 * @see UserProvider
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public interface User extends Principal {

    /**
     * Thread-local reference to the current user. Typically managed by the Flux Capacitor runtime and automatically
     * injected for message handling.
     */
    ThreadLocal<User> current = new ThreadLocal<>();

    /**
     * Returns the user currently associated with the executing thread.
     *
     * @param <U> the expected user subtype
     * @return the current {@code User}, or {@code null} if none is set
     */
    @SuppressWarnings("unchecked")
    static <U extends User> U getCurrent() {
        return (U) current.get();
    }

    /**
     * Executes a callable task with this user set as the current thread-local user. Restores the previous user (if any)
     * after execution.
     *
     * @param <T> the type of result returned by the callable
     * @param f   the callable task to run
     * @return the result of the callable
     */
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

    /**
     * Executes a runnable task with this user set as the current thread-local user. Restores the previous user (if any)
     * after execution.
     *
     * @param f the task to run
     */
    @SneakyThrows
    default void run(ThrowingRunnable f) {
        apply(() -> {
            f.run();
            return null;
        });
    }

    /**
     * Indicates whether this user has the specified role.
     *
     * @param role a role string such as {@code "admin"} or {@code "viewer"}
     * @return {@code true} if the user has the role, {@code false} otherwise
     */
    boolean hasRole(String role);
}
