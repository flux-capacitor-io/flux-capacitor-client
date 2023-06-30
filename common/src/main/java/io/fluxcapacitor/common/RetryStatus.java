/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
