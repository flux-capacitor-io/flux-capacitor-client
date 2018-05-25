/*
 * Copyright (c) 2016-2018 Flux Capacitor. 
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

import io.fluxcapacitor.javaclient.common.Message;

import java.util.List;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.EVENT;

public class ResultValidator extends AbstractResultValidator {
    private final List<Message> resultingEvents;
    private final List<Message> resultingCommands;

    public ResultValidator(Object actualResult,
                           List<Message> resultingEvents,
                           List<Message> resultingCommands) {
        super(actualResult);
        this.resultingEvents = resultingEvents;
        this.resultingCommands = resultingCommands;
    }

    @Override
    public Then expectOnlyEvents(List<?> events) {
        return expectOnlyMessages(asMessages(events, EVENT), resultingEvents);
    }

    @Override
    public Then expectEvents(List<?> events) {
        return expectMessages(asMessages(events, EVENT), resultingEvents);
    }

    @Override
    public Then expectOnlyCommands(List<?> commands) {
        return expectOnlyMessages(asMessages(commands, COMMAND), resultingCommands);
    }

    @Override
    public Then expectCommands(List<?> commands) {
        return expectMessages(asMessages(commands, COMMAND), resultingCommands);
    }
}
