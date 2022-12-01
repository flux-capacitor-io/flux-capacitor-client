package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;

public interface ContentFilter {

    /**
     * Modify given value before it's passed to the given viewer. See {@link FilterContent} for info on how to filter
     * the value.
     */
    <T> T filterContent(T value, User viewer);
}
