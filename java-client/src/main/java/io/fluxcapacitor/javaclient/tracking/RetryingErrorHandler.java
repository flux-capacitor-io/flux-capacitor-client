package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;
import static java.lang.String.format;

@Slf4j
@AllArgsConstructor
public class RetryingErrorHandler implements ErrorHandler {
    private final int retries;
    private final Duration delay;
    private final Predicate<Exception> errorFilter;
    private final boolean throwOnFailure;
    private final boolean logFunctionalErrors;

    public static RetryingErrorHandler forAnyError() {
        return new RetryingErrorHandler(e -> true);
    }

    public RetryingErrorHandler(Predicate<Exception> errorFilter) {
        this(5, Duration.ofSeconds(2), errorFilter, false, true);
    }

    @Override
    public void handleError(Exception error, String errorMessage, Runnable retryFunction) throws Exception {
        if (!errorFilter.test(error)) {
            logError(format("%s. Not retrying, %s", errorMessage, throwOnFailure ? "propagating error" : "continuing."),
                     error);
            if (throwOnFailure) {
                throw error;
            }
            return;
        }
        logError(format("%s. Retrying up to %s times.", errorMessage, retries), error);
        AtomicInteger remainingRetries = new AtomicInteger(retries);
        boolean success = retryOnFailure(retryFunction, delay,
                                         e -> errorFilter.test(e) && remainingRetries.decrementAndGet() > 0);
        if (success) {
            log.info("Message handling was successful on retry");
        } else {
            if (throwOnFailure) {
                logMessage("Propagating error", isTechnicalError(error));
                throw error;
            } else {
                logMessage(format("%s. Not retrying any further. Continuing with next handler.", errorMessage),
                           isTechnicalError(error));
            }
        }
    }

    protected void logError(String errorMessage, Exception error) {
        if (isTechnicalError(error)) {
            log.error("{}. Continuing...", errorMessage, error);
        } else if (logFunctionalErrors) {
            log.warn("{}. Continuing...", errorMessage, error);
        }
    }

    protected void logMessage(String errorMessage, boolean severe) {
        if (severe) {
            log.error(errorMessage);
        } else if (logFunctionalErrors) {
            log.warn(errorMessage);
        }
    }

    protected boolean isTechnicalError(Exception error) {
        return !(error instanceof FunctionalException);
    }
}
