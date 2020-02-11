package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;

public class UnauthenticatedException extends FunctionalException {
    public UnauthenticatedException(String message) {
        super(message);
    }
}
