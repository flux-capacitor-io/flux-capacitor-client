package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;

public class UnauthorizedException extends FunctionalException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
