package io.fluxcapacitor.javaclient.common;

@FunctionalInterface
public interface IdentityProvider {
    String nextId();
}
