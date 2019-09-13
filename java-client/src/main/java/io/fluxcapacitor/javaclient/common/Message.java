package io.fluxcapacitor.javaclient.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

import java.time.Clock;
import java.time.Instant;

@Value
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public class Message {
    private static final ThreadLocal<Clock> clock = ThreadLocal.withInitial(Clock::systemUTC);
    public static IdentityProvider identityProvider = new UuidFactory();
    
    @Wither
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    Object payload;
    Metadata metadata;
    MessageType messageType;
    String messageId;
    Instant timestamp;

    public Message(Object payload, MessageType messageType) {
        this(payload, Metadata.empty(), messageType, identityProvider.nextId(), clock.get().instant());
    }

    public Message(Object payload, Metadata metadata, MessageType messageType) {
        this(payload, metadata, messageType, identityProvider.nextId(), clock.get().instant());
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
