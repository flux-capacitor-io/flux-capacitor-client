package io.fluxcapacitor.javaclient.test;

public class GivenWhenThenAssertionError extends AssertionError {
    public GivenWhenThenAssertionError(String message) {
        super(message);
    }

    public GivenWhenThenAssertionError(String message, Throwable cause) {
        super(message, cause);
    }
}
