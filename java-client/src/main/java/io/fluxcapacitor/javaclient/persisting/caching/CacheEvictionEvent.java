package io.fluxcapacitor.javaclient.persisting.caching;

import lombok.Value;

@Value
public
class CacheEvictionEvent {
    Object id;
    Reason reason;

    enum Reason {
        manual, size, memoryPressure
    }
}
