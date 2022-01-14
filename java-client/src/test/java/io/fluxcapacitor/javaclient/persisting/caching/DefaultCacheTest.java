package io.fluxcapacitor.javaclient.persisting.caching;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultCacheTest {

    private final DefaultCache defaultCache = new DefaultCache();

    @Test
    void testPutAndGet() {
        defaultCache.put("foo", "bar");
        assertEquals("bar", defaultCache.getIfPresent("foo"));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testAddingNullNotAllowed() {
        assertThrows(NullPointerException.class, () -> defaultCache.put("id", null));
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