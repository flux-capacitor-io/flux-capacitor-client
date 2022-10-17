package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;

public class ForeverRetryingErrorHandler extends RetryingErrorHandler {

    public ForeverRetryingErrorHandler() {
        this(Duration.ofSeconds(10), e -> !(e instanceof FunctionalException), true, e -> e);
    }

    public ForeverRetryingErrorHandler(Duration delay, Predicate<Throwable> errorFilter, boolean logFunctionalErrors,
                                       Function<Throwable, ?> errorMapper) {
        super(-1, delay, errorFilter, false, logFunctionalErrors, errorMapper);
    }
}
