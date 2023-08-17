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

package io.fluxcapacitor.testserver.endpoints.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.ClientEvent;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;
import static java.lang.System.currentTimeMillis;
import static java.util.UUID.randomUUID;

@AllArgsConstructor
@Slf4j
public class DefaultMetricsLog implements MetricsLog {
    private final GatewayClient store;
    private final ObjectMapper objectMapper;
    private final ExecutorService workerPool;

    public DefaultMetricsLog(GatewayClient store) {
        this(store, Executors.newSingleThreadExecutor(newThreadFactory("DefaultMetricsLog")));
    }

    public DefaultMetricsLog(GatewayClient store, ExecutorService workerPool) {
        this(store, new ObjectMapper(), workerPool);
    }

    @Override
    public void registerMetrics(ClientEvent event, Metadata metadata) {
        workerPool.submit(() -> {
            try {
                Revision revision = event.getClass().getAnnotation(Revision.class);
                byte[] payload = objectMapper.writeValueAsBytes(event);
                store.send(Guarantee.NONE, new SerializedMessage(
                        new Data<>(payload, event.getClass().getName(), revision == null ? 0 : revision.value(), Data.JSON_FORMAT),
                        metadata, randomUUID().toString(), currentTimeMillis()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize metrics {}", event, e);
            }
        });
    }
}
