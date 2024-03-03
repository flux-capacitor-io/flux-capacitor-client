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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.localhandler.PackageLocalHandler;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.METRICS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class LocalHandlerTest {

    private final TestFixture testFixture = TestFixture.createAsync(new PublishingLocalHandler(), new PackageLocalHandler()).spy();

    @Test
    void testMessagePublication() {
        testFixture.whenCommand("a").expectThat(fc -> verify(fc.client().getGatewayClient(COMMAND)).send(any(), any()));
    }

    @Test
    void testPackageHandler() {
        testFixture.whenCommand("a".getBytes()).expectResult(false);
    }

    @Test
    void testMetricsPublication() {
        testFixture.whenCommand("a").expectThat(fc -> verify(fc.client().getGatewayClient(METRICS)).send(any(), any()));
    }

    @Test
    void testNoMessagePublication() {
        testFixture.whenCommand(1)
                .expectThat(fc -> verify(fc.client().getGatewayClient(COMMAND), never()).send(any(), any()));
    }

    @Test
    void testNoMetricsPublication() {
        testFixture.whenCommand(1.1f)
                .expectThat(fc -> verify(fc.client().getGatewayClient(METRICS), never()).send(any(), any()));
    }

    @LocalHandler(logMessage = true, logMetrics = true)
    private static class PublishingLocalHandler {
        @HandleCommand
        String handle(String command) {
            return command;
        }

        @HandleCommand
        @LocalHandler(logMessage = false)
        Integer handle(Integer command) {
            return command;
        }

        @HandleCommand
        @LocalHandler(logMetrics = false)
        Float handle(Float command) {
            return command;
        }
    }

}
