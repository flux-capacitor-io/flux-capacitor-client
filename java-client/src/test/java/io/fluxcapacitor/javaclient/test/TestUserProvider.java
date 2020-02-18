package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import java.util.Optional;

@AllArgsConstructor
public class TestUserProvider implements UserProvider {
    @Delegate(excludes = ExcludedMethods.class)
    private final UserProvider delegate;

    @Override
    public User getActiveUser() {
        return Optional.ofNullable(delegate.getActiveUser()).orElse(getSystemUser());
    }

    private interface ExcludedMethods {
        User getActiveUser();
    }
}
