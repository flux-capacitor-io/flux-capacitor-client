package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Arrays;
import java.util.List;

@Value
@AllArgsConstructor
public class MockUser implements User {
    List<String> roles;

    public MockUser(String... roles) {
        this(Arrays.asList(roles));
    }

    @Override
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public String getName() {
        return "mockUser";
    }
}
