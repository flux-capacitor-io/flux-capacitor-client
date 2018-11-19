package io.fluxcapacitor.javaclient.test;

import org.opentest4j.AssertionFailedError;

public class GivenWhenThenAssertionError extends AssertionFailedError {
    public GivenWhenThenAssertionError(String message) {
        super(message);
    }

    public GivenWhenThenAssertionError(String message, Throwable cause) {
        super(message, cause);
    }

    public GivenWhenThenAssertionError(String message, Object expected, Object actual) {
        super(message, expected, actual);
    }
}
