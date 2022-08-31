/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.publishing.dataprotection;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataProtectionInterceptorTest {
    private final TestFixture testFixture = TestFixture.createAsync();

    @Test
    void testSerializedMessageDoesNotContainData() {
        testFixture.registerHandlers(new SomeHandler())
                .whenExecuting(fc -> FluxCapacitor.publishEvent(new SomeEvent("something super secret")))
                .expectEvents(new SomeEvent(null));
    }

    @Test
    void testHandlerDoesGetData() {
        String payload = "something super secret";
        SomeHandler handler = new SomeHandler();
        testFixture.registerHandlers(handler)
                .whenExecuting(fc -> FluxCapacitor.publishEvent(new SomeEvent(payload)))
                .expectThat(fc -> {
                    assertEquals(payload, handler.getLastEvent().getSensitiveData());
                    assertTrue(handler.getLastMetadata().containsKey(DataProtectionInterceptor.METADATA_KEY));
                });
    }

    @Test
    void testDroppingDataPermanently() {
        String payload = "something super secret";
        DroppingHandler droppingHandler = new DroppingHandler();
        SomeHandler secondHandler = new SomeHandler();
        testFixture.registerHandlers(droppingHandler, secondHandler)
                .whenExecuting(fc -> FluxCapacitor.publishEvent(new SomeEvent(payload)))
                .expectThat(fc -> {
                    assertEquals(payload, droppingHandler.getLastEvent().getSensitiveData());
                    assertNull(secondHandler.getLastEvent().getSensitiveData());
                });
    }

    @Test
    void testCommandValidationAfterFieldIsSet() {
        String payload = "something super secret";
        var handler = new ValidatingHandler();
        testFixture.registerHandlers(handler)
                .whenExecuting(fc -> FluxCapacitor.sendCommandAndWait(new ConstrainedCommand(payload)))
                .expectThat(fc -> assertEquals(payload, handler.getLastCommand().getSensitiveData()));
    }

    @Test
    void testNullDataIsIgnored() {
        SomeHandler handler = new SomeHandler();
        testFixture.registerHandlers(handler)
                .whenExecuting(fc -> FluxCapacitor.publishEvent(new SomeEvent(null)))
                .expectEvents(new SomeEvent(null))
                .expectThat(fc -> {
                    assertNull(handler.getLastEvent().getSensitiveData());
                    assertFalse(handler.getLastMetadata().containsKey(DataProtectionInterceptor.METADATA_KEY));
                });
    }

    @Value
    @Builder(toBuilder = true)
    private static class SomeEvent {
        @ProtectData
        String sensitiveData;
    }

    @Value
    @Builder(toBuilder = true)
    private static class ConstrainedCommand {
        @ProtectData
        @NotNull
        String sensitiveData;
    }

    @Getter
    private static class SomeHandler {
        private SomeEvent lastEvent;
        private Metadata lastMetadata;
        private Data<byte[]> data;

        @HandleEvent
        private void handler(SomeEvent event, DeserializingMessage message) {
            lastEvent = event.toBuilder().build();
            lastMetadata = message.getMetadata();
            data = message.getSerializedObject().getData();
        }
    }

    @Getter
    private static class ValidatingHandler {
        private ConstrainedCommand lastCommand;

        @HandleCommand
        private void handler(ConstrainedCommand command) {
            lastCommand = command;
        }
    }

    @Getter
    private static class DroppingHandler {
        private SomeEvent lastEvent;

        @HandleEvent
        @DropProtectedData
        private void handler(SomeEvent event) {
            lastEvent = event.toBuilder().build();
        }
    }

}
