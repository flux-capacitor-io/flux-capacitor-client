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

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.IdentityProvider;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;

public interface Given {

    When givenCommands(Object... commands);

    When givenCommandsByUser(User user, Object... commands);

    When givenDomainEvents(String aggregateId, Object... events);

    When givenEvents(Object... events);

    When given(Consumer<FluxCapacitor> condition);

    When givenSchedules(Schedule... schedules);

    default When givenDocument(Object document, String id, String collection) {
        return givenDocument(document, id, collection, null);
    }

    When givenDocument(Object document, String id, String collection, Instant timestamp);

    When givenDocuments(String collection, Object... documents);

    default When givenExpiredSchedules(Object... schedules) {
        return givenSchedules(
                Arrays.stream(schedules).map(p -> new Schedule(p, UUID.randomUUID().toString(), getClock().instant()))
                        .toArray(Schedule[]::new));
    }

    default When givenNoPriorActivity() {
        return givenCommands();
    }

    When givenTimeAdvancesTo(Instant instant);

    When givenTimeElapses(Duration duration);

    Clock getClock();

    Given withClock(Clock clock);

    IdentityProvider getIdentityProvider();

    Given withIdentityProvider(IdentityProvider identityProvider);

    default Given withFixedTime(Instant time) {
        return withClock(Clock.fixed(time, ZoneId.systemDefault()));
    }

}
