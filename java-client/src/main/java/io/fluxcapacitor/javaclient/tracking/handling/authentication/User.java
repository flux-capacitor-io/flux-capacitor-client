package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.security.Principal;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public interface User extends Principal {
    
    ThreadLocal<User> current = new ThreadLocal<>();
    
    @SuppressWarnings("unchecked")
    static <U extends User> U getCurrent() {
        return (U) current.get();
    }

    boolean hasRole(String role);
}
