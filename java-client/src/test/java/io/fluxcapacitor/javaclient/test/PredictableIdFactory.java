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

package io.fluxcapacitor.javaclient.test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default implementation of {@link PredictableIdentityProvider} for test environments.
 * <p>
 * This implementation generates predictable, repeatable identifiers for both functional and technical use.
 * Functional IDs are simple incrementing integers (as strings), while technical IDs are deterministically
 * generated from a separate counter using {@link UUID#nameUUIDFromBytes(byte[])}.
 * <p>
 * Keeping the counters separate ensures test reproducibility while avoiding cross-contamination
 * between identifier types. This behavior is critical for existing tests relying on specific sequences.
 */
public class PredictableIdFactory implements PredictableIdentityProvider {

    private final AtomicInteger functionalCounter = new AtomicInteger(), technicalCounter = new AtomicInteger();

    /**
     * Returns a predictable functional ID using an incrementing counter.
     * <p>
     * The first call returns {@code "0"}, the next {@code "1"}, and so on.
     *
     * @return a deterministic functional ID as a string
     */
    @Override
    public String nextFunctionalId() {
        return Integer.toString(functionalCounter.getAndIncrement());
    }

    /**
     * Returns a deterministic technical ID derived from a separate counter using UUID name-based generation.
     * <p>
     * This ensures a stable sequence of UUIDs across test runs without affecting the functional ID sequence.
     *
     * @return a predictable UUID string for technical use
     */
    @Override
    public String nextTechnicalId() {
        int index = technicalCounter.getAndIncrement();
        UUID uuid = UUID.nameUUIDFromBytes(("technical-id-" + index).getBytes());
        return uuid.toString();
    }
}
