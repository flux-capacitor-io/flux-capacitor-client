package io.fluxcapacitor.common;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

import java.time.Duration;
import java.time.Instant;

@Value
@AllArgsConstructor
public class RetryStatus {
    RetryConfiguration retryConfiguration;
    Object task;
    @With
    int numberOfTimesRetried;
    @With
    Exception exception;
    Instant initialErrorTimestamp;
    @With
    Instant previousErrorTimestamp;

    public RetryStatus(RetryConfiguration retryConfiguration, Object task, Exception exception) {
        this.retryConfiguration = retryConfiguration;
        this.task = task;
        this.numberOfTimesRetried = 0;
        this.exception = exception;
        this.initialErrorTimestamp = Instant.now();
        this.previousErrorTimestamp = Instant.now();
    }
    
    public boolean hasCrossedThreshold(Duration threshold) {
        return Duration.between(initialErrorTimestamp, Instant.now()).compareTo(threshold) >= 0 
                && Duration.between(initialErrorTimestamp, previousErrorTimestamp).compareTo(threshold) < 0;
    }
}
