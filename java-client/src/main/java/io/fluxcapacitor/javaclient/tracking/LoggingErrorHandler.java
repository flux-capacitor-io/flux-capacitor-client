package io.fluxcapacitor.javaclient.tracking;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum LoggingErrorHandler implements ErrorHandler {
    INSTANCE;

    @Override
    public void handleError(Exception error, String errorMessage, Runnable retryFunction) {
        log.error("{}. Continuing...", errorMessage, error);
    }
}
