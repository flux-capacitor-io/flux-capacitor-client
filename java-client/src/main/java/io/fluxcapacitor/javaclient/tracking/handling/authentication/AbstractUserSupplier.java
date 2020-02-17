package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.api.Metadata;
import lombok.AllArgsConstructor;

import java.util.Optional;

@AllArgsConstructor
public abstract class AbstractUserSupplier implements UserSupplier {
    
    private final String metadataKey;
    private final Class<? extends User> userClass;

    public AbstractUserSupplier(Class<? extends User> userClass) {
        this("$user", userClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U extends User> U fromMetadata(Metadata metadata) {
        return (U) Optional.ofNullable(metadata.get(metadataKey, userClass)).orElse(null);
    }

    @Override
    public Metadata removeFromMetadata(Metadata metadata) {
        metadata.remove(metadataKey);
        return metadata;
    }

    @Override
    public Metadata addToMetadata(Metadata metadata, User user) {
        metadata.put(metadataKey, user);
        return metadata;
    }

    @Override
    public Metadata asMetadata(User user) {
        return Metadata.from(metadataKey, user);
    }
    
}
