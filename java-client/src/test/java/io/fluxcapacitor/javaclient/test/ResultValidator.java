/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.scheduling.Schedule;

import java.util.List;

public class ResultValidator extends AbstractResultValidator {
    private final List<Message> resultingEvents;
    private final List<Message> resultingCommands;
    private final List<Schedule> resultingSchedules;

    public ResultValidator(FluxCapacitor fluxCapacitor, Object actualResult,
                           List<Message> resultingEvents,
                           List<Message> resultingCommands,
                           List<Schedule> resultingSchedules) {
        super(fluxCapacitor, actualResult);
        this.resultingEvents = resultingEvents;
        this.resultingCommands = resultingCommands;
        this.resultingSchedules = resultingSchedules;
    }

    @Override
    public Then expectOnlyEvents(List<?> events) {
        return expectOnlyMessages(asMessages(events), resultingEvents);
    }

    @Override
    public Then expectEvents(List<?> events) {
        return expectMessages(asMessages(events), resultingEvents);
    }

    @Override
    public Then expectNoEventsLike(List<?> events) {
        return expectNoMessagesLike(asMessages(events), resultingEvents);
    }

    @Override
    public Then expectOnlyCommands(List<?> commands) {
        return expectOnlyMessages(asMessages(commands), resultingCommands);
    }

    @Override
    public Then expectCommands(List<?> commands) {
        return expectMessages(asMessages(commands), resultingCommands);
    }

    @Override
    public Then expectNoCommandsLike(List<?> commands) {
        return expectNoMessagesLike(asMessages(commands), resultingCommands);
    }

    @Override
    public Then expectOnlySchedules(List<?> schedules) {
        return expectOnlyScheduledMessages(asMessages(schedules), resultingSchedules);
    }

    @Override
    public Then expectSchedules(List<?> schedules) {
        return expectScheduledMessages(asMessages(schedules), resultingSchedules);
    }

    @Override
    public Then expectNoSchedulesLike(List<?> schedules) {
        return expectNoMessagesLike(asMessages(schedules), resultingSchedules);
    }
}
