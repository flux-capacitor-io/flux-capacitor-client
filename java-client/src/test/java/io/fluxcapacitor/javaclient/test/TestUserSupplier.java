package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.tracking.handling.authentication.AbstractUserSupplier;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserSupplier;

import java.util.Optional;

public class TestUserSupplier extends AbstractUserSupplier {

    public enum TestUser implements User {
        INSTANCE;

        @Override
        public boolean hasRole(String role) {
            return true;
        }

        @Override
        public String getName() {
            return "test";
        }
    }
    
    private static final User systemUser =
            Optional.ofNullable(UserSupplier.defaultUserSupplier).map(UserSupplier::getSystemUser)
                    .orElse(TestUser.INSTANCE);

    public static final TestUserSupplier INSTANCE = new TestUserSupplier();

    private TestUserSupplier() {
        super(systemUser.getClass());
    }

    @Override
    public User getSystemUser() {
        return systemUser;
    }

    @Override
    public User get() {
        return systemUser;
    }
}
