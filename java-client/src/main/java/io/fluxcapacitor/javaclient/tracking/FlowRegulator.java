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

package io.fluxcapacitor.javaclient.tracking;

import java.time.Duration;
import java.util.Optional;

public interface FlowRegulator {

    /**
     * Optionally pause this consumer for a given amount of time. During this time the consumer will not fetch or
     * consume any messages.
     * <p>
     * After the given pause duration, this method will be called again to determine if fetching should continue or be
     * paused for longer. I.e. the consumer will not continue until this method returns an empty optional.
     */
    default Optional<Duration> pauseDuration() {
        return Optional.empty();
    }

}
