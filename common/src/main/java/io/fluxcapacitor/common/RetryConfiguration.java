package io.fluxcapacitor.common;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Value
@Builder
@Slf4j
public class RetryConfiguration {
    @Default
    Duration delay = Duration.ofSeconds(1);
    @Default
    int maxRetries = -1;
    @Default
    Predicate<Exception> errorTest = e -> true;
    @Default
    Consumer<RetryStatus> successLogger = status -> log.info("Task {} completed successfully on retry", status.getTask());
    @Default
    Consumer<RetryStatus> exceptionLogger = status -> {
        if (status.getNumberOfTimesRetried() == 0) {
            log.error("Task {} failed. Retrying every {} ms...", 
                      status.getTask(), status.getRetryConfiguration().getDelay().toMillis(), status.getException());
        } else if (status.getNumberOfTimesRetried() >= status.getRetryConfiguration().getMaxRetries()) {
            log.error("Task {} failed permanently. Not retrying.", status.getTask(), status.getException());
        }
    };
}
