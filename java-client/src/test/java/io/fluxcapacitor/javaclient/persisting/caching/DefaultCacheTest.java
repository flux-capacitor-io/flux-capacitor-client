package io.fluxcapacitor.javaclient.persisting.caching;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultCacheTest {

    private final Cache defaultCache = new DefaultCache();

    @Test
    void testPutAndGet() {
        defaultCache.put("foo", "bar");
        assertEquals("bar", defaultCache.getIfPresent("foo"));
    }

    @Test
    void testAddingNullAllowed() {
        defaultCache.put("id", null);
        assertNull(defaultCache.getIfPresent("id"));
    }

    @Test
    void testComputeIfAbsent() {
        assertEquals("bar", defaultCache.computeIfAbsent("foo", f -> "bar"));
    }

    @Test
    void testComputeIfAbsentWithNullReturnIsAllowed() {
        assertNull(defaultCache.computeIfAbsent("foo", f -> null));
    }
}