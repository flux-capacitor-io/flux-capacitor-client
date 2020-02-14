package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.api.Metadata;
import lombok.Value;

import java.security.Principal;
import java.util.Optional;

public interface User extends Principal {
    String metadataKey = "$SENDER";
    
    ThreadLocal<User> current = new ThreadLocal<>();
    
    @SuppressWarnings("unchecked")
    static <U extends User> U getCurrent() {
        return (U) current.get();
    }

    @SuppressWarnings("unchecked")
    static <U extends User> U fromMetadata(Metadata metadata) {
        return (U) Optional.ofNullable(metadata.get(metadataKey, UserHolder.class)).map(UserHolder::getUser)
                .orElse(null);
    }
    
    static Metadata removeFromMetadata(Metadata metadata) {
        metadata.remove(metadataKey);
        return metadata;
    }
    
    default Metadata addToMetadata(Metadata metadata) {
        metadata.put(metadataKey, new UserHolder(this));
        return metadata;
    }

    boolean hasRole(String role);

    @Value
    class UserHolder {
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
        User user;
    }
}
