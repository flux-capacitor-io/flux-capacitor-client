package io.fluxcapacitor.common;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;

import java.time.Instant;

@Value
@Builder(toBuilder = true)
public class RetryStatus {
    RetryConfiguration retryConfiguration;
    Object task;
    Exception exception;
    @Default int numberOfTimesRetried = 0;
    @Default Instant initialErrorTimestamp = Instant.now();
    
    public RetryStatus afterRetry(Exception exception) {
        return toBuilder().exception(exception).numberOfTimesRetried(numberOfTimesRetried + 1).build();
    }
}
