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

package io.fluxcapacitor.common;

/**
 * Represents a handle for a cancellable registration, such as a subscription, listener, or callback.
 * <p>
 * The {@code Registration} interface is widely used throughout Flux Capacitor to manage lifecycle-bound resources like
 * message consumers, handler tracking, interceptors, and more. Calling {@link #cancel()} releases the associated resource.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Registration registration = tracker.start();
 *
 * // Later
 * registration.cancel(); // Stops tracking
 * }</pre>
 *
 * <h2>Combining multiple registrations</h2>
 * The {@link #merge(Registration)} method allows combining multiple registrations into a single handle.
 * Cancelling the combined registration will cancel both underlying registrations:
 *
 * <pre>{@code
 * Registration r1 = trackerA.start();
 * Registration r2 = trackerB.start();
 * Registration combined = r1.merge(r2);
 *
 * combined.cancel(); // Cancels both r1 and r2
 * }</pre>
 *
 * <h2>No-op Registration</h2>
 * Use {@link #noOp()} to obtain a dummy {@code Registration} that performs no action when cancelled.
 *
 * @see io.fluxcapacitor.common.Registration
 */
@FunctionalInterface
public interface Registration {

    /**
     * Returns a no-op {@code Registration} that does nothing on {@link #cancel()}.
     */
    static Registration noOp() {
        return () -> {};
    }

    /**
     * Cancels the resource or subscription associated with this registration.
     * <p>
     * Calling this method should be idempotent and safe to invoke multiple times.
     */
    void cancel();

    /**
     * Returns a new {@code Registration} that cancels both this and the given {@code otherRegistration}.
     * <p>
     * This is useful when managing multiple resources together:
     * <pre>{@code
     * Registration combined = reg1.merge(reg2);
     * }</pre>
     *
     * @param otherRegistration another registration to combine with this one
     * @return a composite registration
     */
    default Registration merge(Registration otherRegistration) {
        return () -> {
            cancel();
            otherRegistration.cancel();
        };
    }
}
