/*
 * Copyright (c) 2016-2021 Flux Capacitor.
 *
 * Do not copy, cite or distribute without permission.
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

import static java.lang.System.currentTimeMillis;
import static java.util.UUID.randomUUID;

@AllArgsConstructor
@Slf4j
public class DefaultMetricsLog implements MetricsLog {
    private final GatewayClient store;
    private final ObjectMapper objectMapper;
    private final ExecutorService workerPool;

    public DefaultMetricsLog(GatewayClient store) {
        this(store, Executors.newSingleThreadExecutor());
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
                        new Data<>(payload, event.getClass().getName(), revision == null ? 0 : revision.value(), "application/json"),
                        metadata, randomUUID().toString(), currentTimeMillis()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize metrics {}", event, e);
            }
        });
    }
}
