/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.metrics;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.PropertyUtils;
import io.fluxcapacitor.common.api.ClientAction;
import io.fluxcapacitor.common.api.Message;
import io.fluxcapacitor.common.api.tracking.AppendAction;
import io.fluxcapacitor.common.api.tracking.ReadAction;
import io.fluxcapacitor.common.api.tracking.StorePositionAction;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.common.connection.ApplicationProperties;
import io.fluxcapacitor.javaclient.common.connection.ServiceUrlBuilder;
import io.fluxcapacitor.javaclient.tracking.ConsumerService;
import io.fluxcapacitor.javaclient.tracking.Processor;
import io.fluxcapacitor.javaclient.tracking.websocket.WebsocketConsumerService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
public class GraphiteReporter {

    private static final MetricRegistry metrics = new MetricRegistry();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final HandlerInvoker<ClientAction> invoker =
            HandlerInspector.inspect(GraphiteReporter.class, Handler.class, Collections.singletonList(p -> c -> c));

    public static void main(final String[] args) {
        String fluxCapacitorUrl = System.getProperty("fluxCapacitorUrl", "ws://localhost:8080");
        String host = System.getProperty("graphiteHostName", "localhost");
        int port = PropertyUtils.propertyAsInt("port", 2003);
        Graphite graphite = new Graphite(new InetSocketAddress(host, port));

        com.codahale.metrics.graphite.GraphiteReporter
                reporter = com.codahale.metrics.graphite.GraphiteReporter.forRegistry(metrics)
                .prefixedWith("fluxCapacitorClient")
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);
        reporter.start(10, TimeUnit.SECONDS);

        collectMetrics(new ApplicationProperties("graphiteReporter", fluxCapacitorUrl));
    }

    @Handler
    public static void handle(AppendAction action) {
        String meterName = String.format("%s/%s/%s", action.getClass().getSimpleName(), action.getClient(), action.getLog());
        Meter meter = metrics.meter(meterName);
        meter.mark(action.getSize());
    }

    @Handler
    public static void handle(ReadAction action) {
        String meterName = String.format("%s/%s/%s", action.getClass().getSimpleName(), action.getClient(), action.getLog());
        Meter meter = metrics.meter(meterName);
        meter.mark(action.getSize());
    }

    @Handler
    public static void handle(StorePositionAction action) {
        String meterName = String.format("%s/%s/%s", action.getClass().getSimpleName(), action.getClient(), action.getLog());
        Meter meter = metrics.meter(meterName);
        meter.mark();
    }

    private static void collectMetrics(ApplicationProperties applicationProperties) {
        String metricsLogUrl = ServiceUrlBuilder.consumerUrl(MessageType.USAGE, applicationProperties);
        ConsumerService consumerService = new WebsocketConsumerService(metricsLogUrl);
        Processor.startSingle("graphiteReporter", consumerService, messages -> messages.stream().map(
                GraphiteReporter::deserialize).forEach(GraphiteReporter::handle));
    }

    private static ClientAction deserialize(Message message) {
        try {
            return objectMapper.readValue(message.getPayload(), ClientAction.class);
        } catch (IOException e) {
            log.error("Failed to deserialize to ClientAction", e);
            throw new IllegalStateException(e);
        }
    }

    private static void handle(ClientAction action) {
        try {
            invoker.invoke(null, action);
        } catch (Exception e) {
            log.error("Failed to invoke method for ClientAction {}", action, e);
            throw new IllegalStateException(e);
        }
    }

}
