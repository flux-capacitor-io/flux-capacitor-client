package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.serialization.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdTest {

    private final Owner defaultOwner = new Owner();

    @Test
    void serializeId() {
        assertEquals("{\"id\":\"fooBar\"}", JsonUtils.asJson(defaultOwner));
    }

    @Test
    void deserializeId() {
        assertEquals(defaultOwner, JsonUtils.fromJson("{\"id\":\"fooBar\"}", Owner.class));
    }

    @Test
    void convert() {
        assertEquals(defaultOwner, JsonUtils.convertValue(defaultOwner, Owner.class));
    }

    @Test
    void toStringIsLowercaseAndHasPrefix() {
        assertEquals("mock-foobar", defaultOwner.getId().toString());
    }

    @Value
    @AllArgsConstructor
    private static class Owner {
        MockId id;

        public Owner() {
            this("fooBar");
        }

        public Owner(String id) {
           this(new MockId(id));
        }
    }

    private static class MockId extends Id<Owner> {
        protected MockId(String id) {
            super(id, Owner.class, "mock-", false);
        }
    }
}