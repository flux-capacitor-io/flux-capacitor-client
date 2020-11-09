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

package io.fluxcapacitor.javaclient.common.logging;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

@Slf4j
class FluxCapacitorLogbackAppenderTest {

    private final FluxCapacitor fluxCapacitor = TestFixture.create().getFluxCapacitor();

    @BeforeEach
    void setUp() {
        FluxCapacitor.instance.set(fluxCapacitor);
    }

    @AfterEach
    void tearDown() {
        FluxCapacitor.instance.remove();
    }

    @Test
    void testConsoleError() {
        log.error("mock error");
        verify(fluxCapacitor.client().getGatewayClient(MessageType.ERROR)).send(
                argThat((ArgumentMatcher<SerializedMessage>) message ->
                        ConsoleError.class.getName().equals(message.getData().getType())));
    }

    @Test
    void testConsoleWarning() {
        log.warn("mock warning");
        verify(fluxCapacitor.client().getGatewayClient(MessageType.ERROR)).send(
                argThat((ArgumentMatcher<SerializedMessage>) message ->
                        ConsoleWarning.class.getName().equals(message.getData().getType())));
    }
}