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
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.web.WebRequest;

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

    When givenAppliedEvents(String aggregateId, Object... events);

    When givenEvents(Object... events);

    When given(Consumer<FluxCapacitor> condition);

    default When givenSchedules(Schedule... schedules) {
        return given(fc -> Arrays.stream(schedules).forEach(s -> fc.scheduler().schedule(s)));
    }

    default When givenScheduleIfAbsent(Schedule schedule) {
        return given(fc -> fc.scheduler().schedule(schedule, true));
    }

    default When givenDocument(Object document, String id, String collection) {
        return givenDocument(document, id, collection, null);
    }

    default When givenDocument(Object document, String id, String collection, Instant timestamp) {
        return givenDocument(document, id, collection, timestamp, timestamp);
    }

    When givenDocument(Object document, String id, String collection, Instant timestamp, Instant end);

    When givenDocuments(String collection, Object... documents);

    default When givenExpiredSchedules(Object... schedules) {
        return givenSchedules(
                Arrays.stream(schedules).map(p -> new Schedule(p, UUID.randomUUID().toString(), getClock().instant()))
                        .toArray(Schedule[]::new));
    }

    When givenWebRequest(WebRequest webRequest);

    default When givenNoPriorActivity() {
        return givenCommands();
    }

    When givenTimeAdvancesTo(Instant instant);

    When givenTimeElapses(Duration duration);

    Clock getClock();

    Given withClock(Clock clock);

    default Given withFixedTime(Instant time) {
        return withClock(Clock.fixed(time, ZoneId.systemDefault()));
    }

}
