package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class ThrowingErrorHandler implements ErrorHandler {

    private final boolean logFunctionalErrors;
    private final boolean logTechnicalErrors;

    public ThrowingErrorHandler() {
        this(true, true);
    }

    @Override
    public void handleError(Exception error, String errorMessage, Runnable retryFunction) throws Exception {
        logError(error, errorMessage);
        throw error;
    }
    
    protected void logError(Exception error, String errorMessage) {
        if (!(error instanceof FunctionalException)) {
            if (logTechnicalErrors) {
                log.error("{}. Propagating error...", errorMessage, error);
            }
        } else if (logFunctionalErrors) {
            log.warn("{}. Propagating error...", errorMessage, error);
        }
    }
}
