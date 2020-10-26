package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.api.Metadata;
import lombok.AllArgsConstructor;

import java.util.Optional;

@AllArgsConstructor
public abstract class AbstractUserProvider implements UserProvider {

    private final String metadataKey;
    private final Class<? extends User> userClass;

    public AbstractUserProvider(Class<? extends User> userClass) {
        this("$user", userClass);
    }

    @Override
    public User fromMetadata(Metadata metadata) {
        return Optional.ofNullable(metadata.get(metadataKey, userClass)).orElse(null);
    }

    @Override
    public boolean containsUser(Metadata metadata) {
        return metadata.containsKey(metadataKey);
    }

    @Override
    public Metadata removeFromMetadata(Metadata metadata) {
        return metadata.without(metadataKey);
    }

    @Override
    public Metadata addToMetadata(Metadata metadata, User user) {
        return metadata.with(metadataKey, user);
    }

}
