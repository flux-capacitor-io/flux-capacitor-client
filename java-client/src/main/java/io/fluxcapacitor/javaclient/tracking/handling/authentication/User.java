package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import java.security.Principal;

public interface User extends Principal {
    ThreadLocal<User> current = new ThreadLocal<>();
    
    @SuppressWarnings("unchecked")
    static <U extends User> U getCurrent() {
        return (U) current.get();
    }

    boolean hasRole(String role);
}
