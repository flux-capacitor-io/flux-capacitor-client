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

package io.fluxcapacitor.testserver.endpoints;

import io.fluxcapacitor.common.api.scheduling.CancelSchedule;
import io.fluxcapacitor.common.api.scheduling.GetSchedule;
import io.fluxcapacitor.common.api.scheduling.GetScheduleResult;
import io.fluxcapacitor.common.api.scheduling.StoreSchedule;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.testserver.Handle;
import io.fluxcapacitor.testserver.WebsocketEndpoint;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class SchedulingEndpoint extends WebsocketEndpoint {

    private final SchedulingClient store;

    @Handle
    public void handle(StoreSchedule schedule) {
        store.schedule(schedule.getSchedule(), schedule.isIfAbsent(), schedule.getGuarantee());
    }

    @Handle
    public void handle(CancelSchedule cancelSchedule) {
        store.cancelSchedule(cancelSchedule.getScheduleId(), cancelSchedule.getGuarantee());
    }

    @Handle
    public GetScheduleResult handle(GetSchedule request) {
        return new GetScheduleResult(request.getRequestId(), store.getSchedule(request.getScheduleId()));
    }
}
