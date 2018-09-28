package io.fluxcapacitor.javaclient.common.exception;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"localizedMessage", "cause", "stackTrace", "suppressed"})
public abstract class FunctionalException extends RuntimeException {

    public FunctionalException() {
    }

    public FunctionalException(String message) {
        super(message);
    }

    public FunctionalException(String message, Throwable cause) {
        super(message, cause);
    }

    public FunctionalException(Throwable cause) {
        super(cause);
    }

    public FunctionalException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
