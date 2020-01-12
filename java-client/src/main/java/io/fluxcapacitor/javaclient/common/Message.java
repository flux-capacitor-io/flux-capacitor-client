package io.fluxcapacitor.javaclient.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.api.Metadata;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

import java.time.Clock;
import java.time.Instant;

@Value
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public class Message {
    private static final ThreadLocal<Clock> clock = ThreadLocal.withInitial(Clock::systemUTC);
    public static IdentityProvider identityProvider = new UuidFactory();
    
    @With
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    Object payload;
    @With Metadata metadata;
    String messageId;
    Instant timestamp;

    public Message(Object payload) {
        this(payload, Metadata.empty(), identityProvider.nextId(), clock.get().instant());
    }

    public Message(Object payload, Metadata metadata) {
        this(payload, metadata, identityProvider.nextId(), clock.get().instant());
    }

    @SuppressWarnings("unchecked")
    public <R> R getPayload() {
        return (R) payload;
    }

    public static void useCustomClock(Clock customClock) {
        clock.set(customClock);
    }

    public static void useDefaultClock() {
        clock.set(Clock.systemUTC());
    }
}
