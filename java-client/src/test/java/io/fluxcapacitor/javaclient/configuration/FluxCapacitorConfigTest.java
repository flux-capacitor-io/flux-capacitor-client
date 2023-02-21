/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.configuration;

import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.QUERY;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FluxCapacitorConfigTest {

    @Test
    void testAddConsumerWithExistingNameNotAllowed() {
        ConsumerConfiguration config1 =
                ConsumerConfiguration.builder().name("test").build();
        ConsumerConfiguration config2 =
                ConsumerConfiguration.builder().name("test").build();
        assertThrows(IllegalArgumentException.class, () -> DefaultFluxCapacitor.builder()
                .addConsumerConfiguration(config1, QUERY)
                .addConsumerConfiguration(config2, QUERY));
    }

    @Test
    void testAddConsumerWithExistingNameAllowedIfDifferentMessageType() {
        ConsumerConfiguration config1 =
                ConsumerConfiguration.builder().name("test").build();
        ConsumerConfiguration config2 =
                ConsumerConfiguration.builder().name("test").build();
        DefaultFluxCapacitor.builder()
                .addConsumerConfiguration(config1, QUERY)
                .addConsumerConfiguration(config2, COMMAND);
    }
}