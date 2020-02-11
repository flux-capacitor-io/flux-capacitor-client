package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import java.util.function.Supplier;

public interface UserSupplier extends Supplier<User> {
    Class<? extends User> getUserClass();
    User getSystemUser();
}
