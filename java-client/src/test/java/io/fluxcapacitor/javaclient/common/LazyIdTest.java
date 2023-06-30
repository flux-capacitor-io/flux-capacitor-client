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

package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void serializeLazyIdWithSupplier() {
        LazyId lazyId = new LazyId(() -> "123");
        TestFixture.create().whenApplying(fc -> JsonUtils.asJson(lazyId)).expectResult("\"123\"");
        TestFixture.create().whenApplying(fc -> JsonUtils.asJson(lazyId)).expectResult("\"123\"");
        TestFixture.create().whenApplying(fc -> JsonUtils.convertValue(lazyId, LazyId.class)).expectResult(lazyId);
    }

    @Test
    void serializeLazyIdWithNull() {
        AtomicInteger counter = new AtomicInteger();
        LazyId lazyId = new LazyId(() -> {
            counter.incrementAndGet();
            return null;
        });
        assertFalse(lazyId.isComputed());
        TestFixture.create().whenApplying(fc -> JsonUtils.asJson(lazyId)).expectResult("null");
        assertTrue(lazyId.isComputed());
        TestFixture.create().whenApplying(fc -> JsonUtils.asJson(lazyId)).expectResult("null");
        TestFixture.create().whenApplying(fc -> JsonUtils.convertValue(lazyId, LazyId.class))
                .<LazyId>expectResult(r -> r.equals(lazyId) && r.isComputed());
        assertEquals(1, counter.get());
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
    void deserializeNullLazyIdInOwner() {
        var value = new OwnerWithConfigurableId(new LazyId((Object) null), "test");
        var testFixture = TestFixture.create();
        var copy = testFixture.getFluxCapacitor().apply(
                fc -> JsonUtils.fromJson(JsonUtils.asJson(value), OwnerWithConfigurableId.class));
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

    @Value
    @Builder(toBuilder = true)
    private static class OwnerWithConfigurableId {
        LazyId id;
        String value;
    }
}