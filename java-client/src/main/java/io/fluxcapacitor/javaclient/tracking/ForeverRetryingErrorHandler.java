package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;

import java.time.Duration;
import java.util.function.Predicate;

public class ForeverRetryingErrorHandler extends RetryingErrorHandler {

    public ForeverRetryingErrorHandler() {
        this(Duration.ofSeconds(10));
    }

    public ForeverRetryingErrorHandler(Duration delay) {
        this(delay, e -> !(e instanceof FunctionalException));
    }

    public ForeverRetryingErrorHandler(Duration delay, Predicate<Exception> errorFilter) {
        this(delay, errorFilter, true);
    }

    public ForeverRetryingErrorHandler(Duration delay, Predicate<Exception> errorFilter,
                                       boolean logFunctionalErrors) {
        super(-1, delay, errorFilter, false, logFunctionalErrors);
    }
}
