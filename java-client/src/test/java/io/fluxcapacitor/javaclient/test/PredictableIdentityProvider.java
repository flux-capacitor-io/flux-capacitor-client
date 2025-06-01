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

import io.fluxcapacitor.javaclient.common.IdentityProvider;

import java.util.ServiceLoader;

/**
 * IdentityProvider used in testing scenarios to produce predictable and repeatable identifiers.
 * <p>
 * Unlike the default identity provider used in production, this provider ensures that generated IDs
 * are deterministic and reproducible across runs, making test outcomes stable and easier to verify.
 * <p>
 * The default implementation is loaded via {@link ServiceLoader}, allowing users to plug in their own
 * predictable identity provider by registering a service implementation. If no provider is found,
 * {@link PredictableIdFactory} is used as a fallback.
 */
public interface PredictableIdentityProvider extends IdentityProvider {

    /**
     * Returns the default {@link IdentityProvider} used in tests to generate predictable and repeatable IDs.
     * <p>
     * Attempts to load a {@code PredictableIdentityProvider} using the {@link ServiceLoader} mechanism. If none
     * are found, falls back to {@link PredictableIdFactory}.
     *
     * @return the default predictable identity provider for testing
     */
    static IdentityProvider defaultPredictableIdentityProvider() {
        return ServiceLoader.load(PredictableIdentityProvider.class)
            .findFirst()
            .orElseGet(PredictableIdFactory::new);
    }
}
