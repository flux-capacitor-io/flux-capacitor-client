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

package io.fluxcapacitor.testserver.websocket;

import io.fluxcapacitor.common.api.scheduling.CancelSchedule;
import io.fluxcapacitor.common.api.scheduling.GetSchedule;
import io.fluxcapacitor.common.api.scheduling.GetScheduleResult;
import io.fluxcapacitor.common.api.scheduling.Schedule;
import io.fluxcapacitor.common.api.scheduling.SerializedSchedule;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import lombok.AllArgsConstructor;

import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class SchedulingEndpoint extends WebsocketEndpoint {

    private final SchedulingClient store;

    @Handle
    CompletableFuture<Void> handle(Schedule schedule) {
        return store.schedule(schedule.getGuarantee(), schedule.getMessages().toArray(SerializedSchedule[]::new));
    }

    @Handle
    CompletableFuture<Void> handle(CancelSchedule request) {
        return store.cancelSchedule(request.getScheduleId(), request.getGuarantee());
    }

    @Handle
    GetScheduleResult handle(GetSchedule request) {
        return new GetScheduleResult(request.getRequestId(), store.getSchedule(request.getScheduleId()));
    }
}
