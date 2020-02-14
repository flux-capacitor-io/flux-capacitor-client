package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import java.util.function.Supplier;

public interface UserSupplier extends Supplier<User> {
    default User getSystemUser() {
        return DefaultSystemUser.INSTANCE;
    }
}
