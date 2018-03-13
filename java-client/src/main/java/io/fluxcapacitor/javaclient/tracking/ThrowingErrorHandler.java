package io.fluxcapacitor.javaclient.tracking;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum ThrowingErrorHandler implements ErrorHandler {
    INSTANCE;

    @Override
    public void handleError(Exception error, String errorMessage, Runnable retryFunction) throws Exception {
        log.error("{}. Propagating error", errorMessage, error);
        throw error;
    }
}
