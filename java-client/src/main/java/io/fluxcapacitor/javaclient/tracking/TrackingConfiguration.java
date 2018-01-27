/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.Interceptor;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Singular;
import lombok.Value;

import java.time.Duration;
import java.util.List;

@Value
@Builder(builderClassName = "Builder", toBuilder = true)
public class TrackingConfiguration {

    public static final TrackingConfiguration DEFAULT = TrackingConfiguration.builder().build();

    @Default
    int threads = 1;
    @Default
    int maxFetchBatchSize = 1024;
    @Default
    int maxConsumerBatchSize = 1024;
    @Default
    Duration maxWaitDuration = Duration.ofSeconds(60);
    @Default
    Duration retryDelay = Duration.ofSeconds(1);
    @Singular
    List<Interceptor<List<SerializedMessage>, Void>> batchInterceptors;
}
