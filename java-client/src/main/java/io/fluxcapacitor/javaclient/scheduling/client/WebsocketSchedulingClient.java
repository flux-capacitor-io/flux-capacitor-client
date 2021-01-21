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

package io.fluxcapacitor.javaclient.scheduling.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Backlog;
import io.fluxcapacitor.common.api.scheduling.CancelSchedule;
import io.fluxcapacitor.common.api.scheduling.Schedule;
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.Properties;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.util.List;

@Slf4j
@ClientEndpoint
public class WebsocketSchedulingClient extends AbstractWebsocketClient implements SchedulingClient {

    private final Backlog<ScheduledMessage> backlog;

    public WebsocketSchedulingClient(String endPointUrl, Properties properties) {
        this(URI.create(endPointUrl), properties);
    }

    public WebsocketSchedulingClient(URI endpointUri, Properties properties) {
        super(endpointUri, properties, true);
        backlog = new Backlog<>(this::scheduleMessages);
    }

    protected Awaitable scheduleMessages(List<ScheduledMessage> scheduledMessages) {
        return sendAndForget(new Schedule(scheduledMessages));
    }

    @Override
    public Awaitable schedule(ScheduledMessage... schedules) {
        return backlog.add(schedules);
    }

    @Override
    public Awaitable cancelSchedule(String scheduleId) {
        return sendAndForget(new CancelSchedule(scheduleId));
    }
}
