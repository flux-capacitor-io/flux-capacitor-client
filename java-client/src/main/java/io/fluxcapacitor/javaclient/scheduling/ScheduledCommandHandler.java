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

package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.Consumer;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;

import java.util.stream.Stream;

import static io.fluxcapacitor.common.Guarantee.NONE;
import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.javaclient.common.serialization.UnknownTypeStrategy.IGNORE;

@Consumer(name = "ScheduledCommandHandler", typeFilter = "io.fluxcapacitor.javaclient.scheduling.ScheduledCommand")
public class ScheduledCommandHandler {
    @HandleSchedule
    void handle(ScheduledCommand schedule) {
        SerializedMessage command = schedule.getCommand();
        command.setTimestamp(FluxCapacitor.currentTime().toEpochMilli());
        var commands = FluxCapacitor.get().serializer().deserializeMessages(Stream.of(command), COMMAND, IGNORE)
                .map(DeserializingMessage::toMessage).toArray();
        if (commands.length != 0) {
            FluxCapacitor.sendAndForgetCommands(commands);
        } else {
            FluxCapacitor.get().client().getGatewayClient(COMMAND).append(NONE, command);
        }
    }
}
