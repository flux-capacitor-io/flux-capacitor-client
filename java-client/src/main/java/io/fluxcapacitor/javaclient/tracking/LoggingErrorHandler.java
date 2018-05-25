package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum LoggingErrorHandler implements ErrorHandler {
    INSTANCE;

    @Override
    public void handleError(Exception error, String errorMessage, Runnable retryFunction) {
        if (error instanceof FunctionalException) {
            log.warn("{}: {}({}). Continuing...", errorMessage, error.getClass().getSimpleName(), error.getMessage());
        } else {
            log.error("{}. Continuing...", errorMessage, error);
        }
    }
}
