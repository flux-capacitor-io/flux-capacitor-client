package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.common.ObjectUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCacheTest {

    private final Cache subject = new DefaultCache(2);

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
        var thread1 = new Thread(() -> subject.compute("foo", (k, v) -> ObjectUtils.safelyCall(() -> {
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
        assertEquals(Thread.State.TERMINATED, thread1.getState());
        assertEquals(Thread.State.TERMINATED, thread2.getState());
    }

    @SneakyThrows
    @Test
    void testNoLockIfDifferentKey() {
        var latch = new CountDownLatch(1);
        var thread1 = new Thread(() -> subject.compute("foo", (k, v) -> ObjectUtils.safelyCall(() -> {
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
}