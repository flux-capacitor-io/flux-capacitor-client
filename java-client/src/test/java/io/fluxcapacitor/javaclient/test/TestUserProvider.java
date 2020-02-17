package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.common.Message;
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
    public User getUser(Message message) {
        return Optional.ofNullable(delegate.getUser(message)).orElse(getSystemUser());
    }

    private interface ExcludedMethods {
        User getUser(Message message);
    }
}
