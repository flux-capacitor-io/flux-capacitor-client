package io.fluxcapacitor.javaclient.publishing.dataprotection;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.junit.jupiter.api.Test;

import javax.validation.constraints.NotNull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataProtectionInterceptorTest {

    private final JacksonSerializer serializer = new JacksonSerializer();
    private final FluxCapacitor fluxCapacitor = DefaultFluxCapacitor.builder().disableShutdownHook()
            .replaceSerializer(serializer).build(InMemoryClient.newInstance());

    @Test
    void testSerializedMessageDoesNotContainData() {
        SomeHandler handler = new SomeHandler();
        fluxCapacitor.registerLocalHandlers(handler);
        String payload = "something super secret";
        fluxCapacitor.eventGateway().publish(new SomeEvent(payload));
        SomeEvent deserializedEvent = serializer.deserialize(handler.getData());
        assertNull(deserializedEvent.getSensitiveData());
    }

    @Test
    void testHandlerDoesGetData() {
        SomeHandler handler = new SomeHandler();
        fluxCapacitor.registerLocalHandlers(handler);
        String payload = "something super secret";
        fluxCapacitor.eventGateway().publish(new SomeEvent(payload));
        assertEquals(payload, handler.getLastEvent().getSensitiveData());
        assertTrue(handler.getLastMetadata().containsKey(DataProtectionInterceptor.METADATA_KEY));
    }

    @Test
    void testDroppingDataPermanently() {
        DroppingHandler droppingHandler = new DroppingHandler();
        SomeHandler secondHandler = new SomeHandler();
        fluxCapacitor.registerLocalHandlers(droppingHandler, secondHandler);
        String payload = "something super secret";
        fluxCapacitor.eventGateway().publish(new SomeEvent(payload));
        assertEquals(payload, droppingHandler.getLastEvent().getSensitiveData());
        assertNull(secondHandler.getLastEvent().getSensitiveData());
    }

    @Test
    void testCommandValidationAfterFieldIsSet() {
        ValidatingHandler handler = new ValidatingHandler();
        fluxCapacitor.registerLocalHandlers(handler);
        String payload = "something super secret";
        fluxCapacitor.commandGateway().sendAndWait(new ConstrainedCommand(payload));
        assertEquals(payload, handler.getLastCommand().getSensitiveData());
    }

    @Test
    void testNullDataIsIgnored() {
        SomeHandler handler = new SomeHandler();
        fluxCapacitor.registerLocalHandlers(handler);
        fluxCapacitor.eventGateway().publish(new SomeEvent(null));
        assertNull(handler.getLastEvent().getSensitiveData());
        assertFalse(handler.getLastMetadata().containsKey(DataProtectionInterceptor.METADATA_KEY));
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