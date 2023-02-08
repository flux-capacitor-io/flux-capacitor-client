package io.fluxcapacitor.javaclient.persisting.caching;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}