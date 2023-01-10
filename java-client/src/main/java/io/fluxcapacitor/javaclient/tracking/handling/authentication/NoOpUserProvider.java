package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.HasMessage;
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
    public User fromMessage(HasMessage message) {
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
