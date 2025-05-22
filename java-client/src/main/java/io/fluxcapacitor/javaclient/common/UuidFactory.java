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

package io.fluxcapacitor.javaclient.common;

import lombok.AllArgsConstructor;

import java.util.UUID;

/**
 * Default implementation of {@link IdentityProvider} that generates random UUIDs.
 * <p>
 * Optionally removes dashes from the UUID to produce a more compact identifier.
 * </p>
 *
 * <h2>Example Output</h2>
 * <ul>
 *   <li>{@code 61f3c6d26c9c42d3b56b4c0a7e34c939} (if {@code removeDashes = true})</li>
 *   <li>{@code 61f3c6d2-6c9c-42d3-b56b-4c0a7e34c939} (if {@code removeDashes = false})</li>
 * </ul>
 */
@AllArgsConstructor
public class UuidFactory implements IdentityProvider {

    /**
     * Whether to remove dashes from generated UUIDs.
     */
    private final boolean removeDashes;

    /**
     * Creates a {@code UuidFactory} that removes dashes (default behavior).
     */
    public UuidFactory() {
        this(true);
    }

    /**
     * Returns a new UUID string, optionally stripped of dashes.
     *
     * @return a unique identifier string
     */
    @Override
    public String nextFunctionalId() {
        String id = UUID.randomUUID().toString();
        return removeDashes ? id.replace("-", "") : id;
    }
}
