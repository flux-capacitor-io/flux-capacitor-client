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

package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.PredictableUuidFactory;
import io.fluxcapacitor.javaclient.test.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.fluxcapacitor.javaclient.FluxCapacitor.generateId;

public class GivenWhenThenIdentityProviderTest {

    @Test
    void testPredictableIdentityProvider() {
        TestFixture.create().whenApplying(fc -> List.of(generateId(), generateId()))
                .expectResult(List.of("0", "1")::equals);
    }

    @Test
    void testPredictableUuidProvider() {
        TestFixture.create(DefaultFluxCapacitor.builder().replaceIdentityProvider(p -> new PredictableUuidFactory()))
                .whenApplying(fc -> List.of(generateId(), generateId()))
                .expectResult(List.of("cfcd2084-95d5-35ef-a6e7-dff9f98764da",
                                      "c4ca4238-a0b9-3382-8dcc-509a6f75849b")::equals);
    }
}
