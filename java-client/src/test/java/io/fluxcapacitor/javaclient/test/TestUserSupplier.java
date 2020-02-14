package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserSupplier;

public class TestUserSupplier implements UserSupplier {
    @Override
    public User get() {
        return UserSupplier.systemUser;
    }
}
