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
import lombok.Getter;
import lombok.Singular;

import java.time.Duration;
import java.util.List;

@Builder(builderClassName = "Builder", toBuilder = true)
@Getter
public class TrackingConfiguration {

    public static final TrackingConfiguration DEFAULT = TrackingConfiguration.builder().build();

    @Singular
    private final List<Interceptor<List<SerializedMessage>, Void>> batchInterceptors;
    @Default
    private int threads = 1;
    @Default
    private int maxFetchBatchSize = 1024;
    @Default
    private int maxConsumerBatchSize = 1024;
    @Default
    private Duration maxWaitDuration = Duration.ofSeconds(60);
    @Default
    private Duration retryDelay = Duration.ofSeconds(1);
}
