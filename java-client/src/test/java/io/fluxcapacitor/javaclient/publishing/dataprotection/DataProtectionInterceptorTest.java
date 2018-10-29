package io.fluxcapacitor.javaclient.publishing.dataprotection;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataProtectionInterceptorTest {

    private final JacksonSerializer serializer = new JacksonSerializer();
    private final FluxCapacitor fluxCapacitor = DefaultFluxCapacitor.builder().serializer(serializer)
            .build(InMemoryClient.newInstance());

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

    @Value
    @Builder(toBuilder = true)
    private static class SomeEvent {
        @ProtectData
        String sensitiveData;
    }
    
    @Getter
    private static class SomeHandler {
        private SomeEvent lastEvent;
        private Data<byte[]> data;
        
        @HandleEvent
        private void handler(SomeEvent event, DeserializingMessage message) {
            lastEvent = event.toBuilder().build();
            data = message.getSerializedObject().getData();
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