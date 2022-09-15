package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.api.Metadata;
import lombok.Getter;

public class NoOpUserProvider implements UserProvider {
    @Getter
    private static final NoOpUserProvider instance = new NoOpUserProvider();

    @Override
    public User getActiveUser() {
        return null;
    }

    @Override
    public User getSystemUser() {
        return null;
    }

    @Override
    public User fromMetadata(Metadata metadata) {
        return null;
    }

    @Override
    public boolean containsUser(Metadata metadata) {
        return false;
    }

    @Override
    public Metadata removeFromMetadata(Metadata metadata) {
        return metadata;
    }

    @Override
    public Metadata addToMetadata(Metadata metadata, User user) {
        return metadata;
    }
}
