package io.fluxcapacitor.javaclient.common.caching;

import java.util.function.Function;

public interface Cache {

    /**
     * Adds or replaces a value in the cache.
     *
     * @param id    The object id
     * @param value The value to cache
     */
    void put(String id, Object value);

    /**
     * Returns the value associated with the given id. If there is no association, {@code null} is returned.
     *
     * @param id              The object id
     * @param mappingFunction The function to compute a value if the cache is not in the cache
     * @param <T>             the type of object to return from the cache
     * @return The value associated with given id
     */
    <T> T get(String id, Function<? super String, T> mappingFunction);

    /**
     * Invalidates all cache entries.
     */
    void invalidateAll();

}
