package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class LoggingErrorHandler implements ErrorHandler {
    
    private final boolean logFunctionalErrors;

    public LoggingErrorHandler() {
        this(true);
    }

    @Override
    public void handleError(Exception error, String errorMessage, Runnable retryFunction) {
        if (!(error instanceof FunctionalException)) {
            log.error("{}. Continuing...", errorMessage, error);
        } else if (logFunctionalErrors) {
            log.warn("{}: {}({}). Continuing...", errorMessage, error.getClass().getSimpleName(), error.getMessage());
        }
    }
}
