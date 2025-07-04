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

package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.common.DirectExecutorService;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.javaclient.persisting.caching.DefaultCache.SoftCacheReference;
import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static io.fluxcapacitor.javaclient.persisting.caching.CacheEviction.Reason.expiry;
import static io.fluxcapacitor.javaclient.persisting.caching.CacheEviction.Reason.manual;
import static io.fluxcapacitor.javaclient.persisting.caching.CacheEviction.Reason.memoryPressure;
import static io.fluxcapacitor.javaclient.persisting.caching.CacheEviction.Reason.size;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCacheTest {

    private DefaultCache subject = new DefaultCache(2, DirectExecutorService.newInstance(), null);

    @Test
    void testPutAndGet() {
        subject.put("foo", "bar");
        assertEquals("bar", subject.get("foo"));
    }

    @Test
    void testAddingNullAllowed() {
        subject.put("id", null);
        assertNull(subject.get("id"));
    }

    @Test
    void testComputeIfAbsent() {
        assertEquals("bar", subject.computeIfAbsent("foo", f -> "bar"));
    }

    @Test
    void testComputeIfAbsentWithNullReturnIsAllowed() {
        assertNull(subject.computeIfAbsent("foo", f -> null));
    }

    @Test
    void testComputeIfAbsentWithEmptyOptionalReturnedIsStoredButResolvesAsNull() {
        assertNull(subject.computeIfAbsent("foo", f -> Optional.empty()));
        assertTrue(subject.containsKey("foo"));
    }

    @Test
    void testSizeMaintained() {
        subject.put("id1", "test1");
        subject.put("id2", "test2");
        subject.put("id3", "test3");
        assertEquals(2, subject.size());
        assertNull(subject.get("id1"));
    }

    @Test
    void testSizeMaintainedCompute() {
        subject.compute("id1", (k, v) -> "test1");
        subject.compute("id2", (k, v) -> "test2");
        subject.compute("id3", (k, v) -> "test3");
        assertEquals(2, subject.size());
        assertNull(subject.get("id1"));
    }

    @Test
    void testUpdate() {
        subject.put("id1", "test1");
        subject.put("id2", "test2");
        subject.put("id3", "test3");
        subject.put("id1", "test1-2");
        assertEquals(2, subject.size());
        assertEquals(subject.get("id1"), "test1-2");
    }

    @Test
    void testComputeInOtherCompute() {
        subject.compute("id1", (k, v) -> {
            subject.compute("id2", (k2, v2) -> "bar");
            return "foo";
        });
        assertEquals(2, subject.size());
        assertEquals(subject.get("id1"), "foo");
        assertEquals(subject.get("id2"), "bar");
    }

    @Test
    void testComputeInOtherComputeSameKeyAllowed() {
        assertEquals("foo", subject.compute("id1", (k, v) -> {
            subject.compute("id1", (k2, v2) -> "bar");
            return "foo";
        }));
    }

    @SneakyThrows
    @Test
    void testLockingSameKey() {
        var latch = new CountDownLatch(1);
        var thread1 = new Thread(() -> subject.compute("foo", (k, v) -> ObjectUtils.call(() -> {
            latch.await();
            return "bar";
        })));
        thread1.start();
        Thread.sleep(10);
        var thread2 = new Thread(() -> subject.compute("foo", (k, v) -> "bar2"));
        thread2.start();
        Thread.sleep(10);
        assertNull(subject.get("foo"));
        assertEquals(Thread.State.WAITING, thread1.getState());
        assertEquals(Thread.State.BLOCKED, thread2.getState());
        latch.countDown();
        thread2.join();
        assertEquals("bar2", subject.get("foo"));
    }

    @SneakyThrows
    @Test
    void testNoLockIfDifferentKey() {
        var latch = new CountDownLatch(1);
        var thread1 = new Thread(() -> subject.compute("foo", (k, v) -> ObjectUtils.call(() -> {
            latch.await();
            return "bar";
        })));
        thread1.start();
        Thread.sleep(10);
        var thread2 = new Thread(() -> subject.compute("foo2", (k, v) -> "bar2"));
        thread2.start();
        thread2.join();
        assertNull(subject.get("foo"));
        assertEquals("bar2", subject.get("foo2"));
        assertEquals(Thread.State.WAITING, thread1.getState());
        assertEquals(Thread.State.TERMINATED, thread2.getState());
        latch.countDown();
        thread1.join();
        assertEquals("bar", subject.get("foo"));
        assertEquals(Thread.State.TERMINATED, thread1.getState());
    }

    @Nested
    class EvictionListenerTests {
        List<CacheEvictionEvent> evictionEvents = new ArrayList<>();

        @BeforeEach
        void setUp() {
            subject.registerEvictionListener(e -> evictionEvents.add(new CacheEvictionEvent(e.getId(), e.getReason())));
        }

        @Test
        void manualEviction() {
            subject.put("a", new Object());
            subject.remove("a");
            assertEquals(1, evictionEvents.size());
            assertEquals(new CacheEvictionEvent("a", manual), evictionEvents.getFirst());
        }

        @Test
        void manualEvictionViaClear() {
            subject.put("a", new Object());
            subject.clear();
            assertEquals(1, evictionEvents.size());
            assertEquals(new CacheEvictionEvent(null, manual), evictionEvents.getFirst());
        }

        @Test
        void sizeEviction() {
            subject.put("k1", new Object());
            subject.put("k2", new Object());
            subject.put("k3", new Object());
            assertEquals(1, evictionEvents.size());
            assertEquals(new CacheEvictionEvent("k1", size), evictionEvents.getFirst());
        }

        @SneakyThrows
        @Test
        void simulatedMemoryEviction() {
            subject.put("a", new Object());
            SoftCacheReference ref = (SoftCacheReference) subject.getValueMap().get("a");
            ref.clear();
            ref.enqueue();
            Thread.sleep(10);
            assertEquals(1, evictionEvents.size());
            assertEquals(new CacheEvictionEvent("a", memoryPressure), evictionEvents.getFirst());
            assertTrue(subject.isEmpty());
        }

        @SneakyThrows
        @Test
        void expiryEviction() {
            var testFixture = TestFixture.create();
            subject = new DefaultCache(2, DirectExecutorService.newInstance(), Duration.ofSeconds(10), Duration.ofMillis(1), false);
            setUp();
            subject.put("a", new Object());
            assertNotNull(subject.get("a"));
            testFixture.atFixedTime(testFixture.getCurrentTime().plusSeconds(11));
            Thread.sleep(10);
            assertTrue(subject.isEmpty());
            assertEquals(new CacheEvictionEvent("a", expiry), evictionEvents.getFirst());
        }
    }
}