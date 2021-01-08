/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

public interface When extends Given {

    Then whenCommand(Object command);

    Then whenQuery(Object query);

    Then whenEvent(Object event);

    Then whenScheduleExpires(Object schedule);

    Then whenTimeAdvancesTo(Instant instant);

    Then whenTimeElapses(Duration duration);

    Then when(Runnable task);

    Then whenApplying(Callable<?> task);
}
