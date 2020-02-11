package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import java.security.Principal;
import java.util.List;

public interface User extends Principal {
    ThreadLocal<User> current = new ThreadLocal<>();
    
    List<String> getRoles();

    default boolean hasRole(String role) {
        return getRoles().contains(role);
    }
}
