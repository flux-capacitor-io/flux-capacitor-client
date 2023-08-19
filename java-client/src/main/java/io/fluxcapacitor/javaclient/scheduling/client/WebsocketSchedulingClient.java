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

package io.fluxcapacitor.javaclient.scheduling.client;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.scheduling.CancelSchedule;
import io.fluxcapacitor.common.api.scheduling.GetSchedule;
import io.fluxcapacitor.common.api.scheduling.GetScheduleResult;
import io.fluxcapacitor.common.api.scheduling.Schedule;
import io.fluxcapacitor.common.api.scheduling.SerializedSchedule;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.ClientConfig;
import jakarta.websocket.ClientEndpoint;

import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@ClientEndpoint
public class WebsocketSchedulingClient extends AbstractWebsocketClient implements SchedulingClient {

    public WebsocketSchedulingClient(String endPointUrl, ClientConfig clientConfig) {
        this(URI.create(endPointUrl), clientConfig);
    }

    public WebsocketSchedulingClient(URI endpointUri, ClientConfig clientConfig) {
        this(endpointUri, clientConfig, true);
    }

    public WebsocketSchedulingClient(URI endpointUri, ClientConfig clientConfig, boolean sendMetrics) {
        super(endpointUri, clientConfig, sendMetrics, clientConfig.getGatewaySessions().get(MessageType.SCHEDULE));
    }

    @Override
    public CompletableFuture<Void> schedule(Guarantee guarantee, SerializedSchedule... schedules) {
        return sendCommand(new Schedule(Arrays.asList(schedules), guarantee));
    }

    @Override
    public CompletableFuture<Void> cancelSchedule(String scheduleId, Guarantee guarantee) {
        return sendCommand(new CancelSchedule(scheduleId, guarantee));
    }

    @Override
    public SerializedSchedule getSchedule(String scheduleId) {
        return this.<GetScheduleResult>sendAndWait(new GetSchedule(scheduleId)).getSchedule();
    }
}
