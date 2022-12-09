package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LazyIdTest {

    @Test
    void serializeLazyId() {
        LazyId lazyId = new LazyId();
        TestFixture.create().whenApplying(fc -> JsonUtils.asJson(lazyId)).expectResult("\"0\"");
        TestFixture.create().whenApplying(fc -> JsonUtils.asJson(lazyId)).expectResult("\"0\"");
        TestFixture.create().whenApplying(fc -> JsonUtils.convertValue(lazyId, LazyId.class)).expectResult(lazyId);
    }

    @Test
    void serializeLazyIdWithGivenId() {
        LazyId lazyId = new LazyId("123");
        TestFixture.create().whenApplying(fc -> JsonUtils.asJson(lazyId)).expectResult("\"123\"");
        TestFixture.create().whenApplying(fc -> JsonUtils.asJson(lazyId)).expectResult("\"123\"");
        TestFixture.create().whenApplying(fc -> JsonUtils.convertValue(lazyId, LazyId.class)).expectResult(lazyId);
    }

    @Test
    void serializeInOwnerObject() {
        Owner value = new Owner("test");
        TestFixture.create().whenApplying(fc -> JsonUtils.convertValue(value, Owner.class))
                .expectResult(value)
                .<Owner>expectResult(o -> new LazyId("0").equals(o.getId()));

        TestFixture.create().whenApplying(fc -> JsonUtils.convertValue(value.toBuilder().build(), Owner.class))
                .expectResult(value).<Owner>expectResult(o -> new LazyId("0").equals(o.getId()));
    }

    @Test
    void deserializeToOwnerObject() {
        Owner value = new Owner("test");
        var testFixture = TestFixture.create();
        Owner copy = testFixture.getFluxCapacitor().apply(
                fc -> JsonUtils.fromJson(JsonUtils.asJson(value), Owner.class));
        assertEquals(value, copy);
    }

    @Test
    void testToString() {
        LazyId lazyId = new LazyId();
        TestFixture.create().whenApplying(fc -> lazyId.toString()).expectResult("0");
    }

    @Test
    void testEquals() {
        LazyId lazyId = new LazyId();
        LazyId same = new LazyId("0");
        LazyId other = new LazyId();
        TestFixture.create().whenApplying(fc -> lazyId.equals(other)).expectResult(false);
        TestFixture.create().whenApplying(fc -> lazyId.equals(same)).expectResult(true);
    }

    @Value
    @Builder(toBuilder = true)
    private static class Owner {
        LazyId id = new LazyId();
        String value;
    }
}