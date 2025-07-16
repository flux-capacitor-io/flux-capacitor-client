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

package io.fluxcapacitor.testserver.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.JsonType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.common.tracking.MessageStore;
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
    private final MessageStore store;
    private final ObjectMapper objectMapper;
    private final ExecutorService workerPool;

    public DefaultMetricsLog(MessageStore store) {
        this(store, Executors.newSingleThreadExecutor(newThreadFactory("DefaultMetricsLog")));
    }

    public DefaultMetricsLog(MessageStore store, ExecutorService workerPool) {
        this(store, new ObjectMapper(), workerPool);
    }

    @Override
    public void registerMetrics(JsonType event, Metadata metadata) {
        var finalMetadata = metadata.with("$applicationId", "FluxTestServer");
        workerPool.submit(() -> {
            try {
                Revision revision = event.getClass().getAnnotation(Revision.class);
                byte[] payload = objectMapper.writeValueAsBytes(event);
                store.append(new SerializedMessage(
                        new Data<>(payload, event.getClass().getName(), revision == null ? 0 : revision.value(), Data.JSON_FORMAT),
                        finalMetadata, randomUUID().toString(), currentTimeMillis()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize metrics {}", event, e);
            }
        });
    }
}
