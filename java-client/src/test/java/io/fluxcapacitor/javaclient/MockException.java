package io.fluxcapacitor.javaclient;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;

public class MockException extends FunctionalException {

    public MockException() {
    }

    public MockException(String message) {
        super(message);
    }

    public MockException(String message, Throwable cause) {
        super(message, cause);
    }

    public MockException(Throwable cause) {
        super(cause);
    }

    public MockException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
