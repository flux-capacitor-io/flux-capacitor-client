package io.fluxcapacitor.javaclient.tracking.handling.authentication;

public enum DefaultSystemUser implements User {
    
    INSTANCE;

    @Override
    public String getName() {
        return "system";
    }

    @Override
    public boolean hasRole(String role) {
        return true;
    }
}
