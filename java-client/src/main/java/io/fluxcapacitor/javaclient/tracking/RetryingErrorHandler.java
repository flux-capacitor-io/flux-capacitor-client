package io.fluxcapacitor.javaclient.tracking;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;

@Slf4j
@AllArgsConstructor
public class RetryingErrorHandler implements ErrorHandler {
    private final int retries;
    private final Duration delay;
    private final Predicate<Exception> errorFilter;
    private final boolean throwOnFailure;

    public static RetryingErrorHandler forAnyError() {
        return new RetryingErrorHandler(e -> true);
    }

    public RetryingErrorHandler(Predicate<Exception> errorFilter) {
        this(5, Duration.ofSeconds(2), errorFilter, false);
    }

    @Override
    public void handleError(Exception error, String errorMessage, Runnable retryFunction) throws Exception {
        if (!errorFilter.test(error)) {
            log.error("{}. Not retrying, {}", errorMessage, throwOnFailure ? "propagating error" : "continuing.", error);
            if (throwOnFailure) {
                throw error;
            }
            return;
        }
        log.error("{}. Retrying up to {} times.", errorMessage, retries, error);
        AtomicInteger remainingRetries = new AtomicInteger(retries);
        boolean success = retryOnFailure(retryFunction, delay,
                                         e -> errorFilter.test(e) && remainingRetries.decrementAndGet() > 0);
        if (success) {
            log.info("Message handling was successful on retry");
        } else {
            if (throwOnFailure) {
                log.error("Propagating error");
                throw error;
            } else {
                log.error("{}. Not retrying any further. Continuing with next handler.", errorMessage);
            }
        }
    }
}
