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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;


@Slf4j
public class FluxCapacitorConfigTest {

    @Test
    void testAddConsumerWithExistingNameNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> DefaultFluxCapacitor.builder()
                .addConsumerConfiguration(ConsumerConfiguration.getDefault(MessageType.QUERY)));
    }

    @Test
    void testUpdateConsumerToUseExistingNameNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> DefaultFluxCapacitor.builder()
                .addConsumerConfiguration(ConsumerConfiguration.builder().messageType(MessageType.QUERY).name("foo").build())
                .configureDefaultConsumer(MessageType.QUERY,
                                          consumerConfiguration -> consumerConfiguration.toBuilder().name("foo")
                                                  .build()));
    }
}