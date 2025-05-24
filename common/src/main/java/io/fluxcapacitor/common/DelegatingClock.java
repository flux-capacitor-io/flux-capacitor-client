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

import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A concrete implementation of a {@link Clock} that delegates its method calls to another {@link Clock}
 * instance, allowing runtime manipulation of the delegated clock.
 *
 * <p>This class is useful when you need to dynamically switch the clock's implementation during runtime,
 * for example, testing scenarios requiring adjustable or controlled time flows.
 *
 * <p>The delegated clock instance is stored atomically to ensure thread-safe updates.
 */
@AllArgsConstructor
public class DelegatingClock extends Clock {
    private final AtomicReference<Clock> delegate = new AtomicReference<>(Clock.systemUTC());

    public void setDelegate(@NonNull Clock delegate) {
        this.delegate.set(delegate);
    }

    @Override
    public ZoneId getZone() {
        return delegate.get().getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return delegate.get().withZone(zone);
    }

    @Override
    public Instant instant() {
        return delegate.get().instant();
    }
}
