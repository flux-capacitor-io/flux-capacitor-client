package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import java.util.function.Supplier;

public interface UserSupplier extends Supplier<User> {
    User systemUser = DefaultSystemUser.INSTANCE;
    
    default User getSystemUser() {
        return systemUser;
    }
    
}
