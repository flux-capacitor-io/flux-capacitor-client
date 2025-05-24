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

package io.fluxcapacitor.testserver;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.tracking.HasMessageStore;
import io.fluxcapacitor.common.tracking.MessageStore;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.LocalClient;
import io.fluxcapacitor.javaclient.scheduling.client.LocalSchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.testserver.metrics.DefaultMetricsLog;
import io.fluxcapacitor.testserver.metrics.MetricsLog;
import io.fluxcapacitor.testserver.metrics.NoOpMetricsLog;
import io.fluxcapacitor.testserver.scheduling.TestServerScheduleStore;
import io.fluxcapacitor.testserver.websocket.ConsumerEndpoint;
import io.fluxcapacitor.testserver.websocket.EventSourcingEndpoint;
import io.fluxcapacitor.testserver.websocket.KeyValueEndPoint;
import io.fluxcapacitor.testserver.websocket.ProducerEndpoint;
import io.fluxcapacitor.testserver.websocket.SchedulingEndpoint;
import io.fluxcapacitor.testserver.websocket.SearchEndpoint;
import io.undertow.Undertow;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.PathHandler;
import jakarta.websocket.Endpoint;
import jakarta.websocket.Session;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.ERROR;
import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.MessageType.METRICS;
import static io.fluxcapacitor.common.MessageType.NOTIFICATION;
import static io.fluxcapacitor.common.MessageType.QUERY;
import static io.fluxcapacitor.common.MessageType.RESULT;
import static io.fluxcapacitor.common.MessageType.SCHEDULE;
import static io.fluxcapacitor.common.MessageType.WEBREQUEST;
import static io.fluxcapacitor.common.MessageType.WEBRESPONSE;
import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.ObjectUtils.newThreadName;
import static io.fluxcapacitor.common.ServicePathBuilder.consumerPath;
import static io.fluxcapacitor.common.ServicePathBuilder.eventSourcingPath;
import static io.fluxcapacitor.common.ServicePathBuilder.keyValuePath;
import static io.fluxcapacitor.common.ServicePathBuilder.producerPath;
import static io.fluxcapacitor.common.ServicePathBuilder.schedulingPath;
import static io.fluxcapacitor.common.ServicePathBuilder.searchPath;
import static io.fluxcapacitor.testserver.websocket.WebsocketDeploymentUtils.deploy;
import static io.fluxcapacitor.testserver.websocket.WebsocketDeploymentUtils.deployFromSession;
import static io.fluxcapacitor.testserver.websocket.WebsocketDeploymentUtils.getProjectId;
import static io.undertow.Handlers.path;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;

@Slf4j
public class TestServer {

    private static final Function<String, Client> clients = memoize(
            projectId -> new TestServerProject(LocalClient.newInstance()));
    private static final Function<String, MetricsLog> metricsLogSupplier =
            memoize(projectId -> new DefaultMetricsLog(getMessageStore(projectId, METRICS)));

    public static void main(final String[] args) {
        start(Integer.getInteger("port", 8080));
    }

    public static void start(int port) {
        PathHandler pathHandler = path();
        for (MessageType messageType : Arrays.asList(METRICS, EVENT, COMMAND, QUERY, RESULT, ERROR, WEBREQUEST, WEBRESPONSE)) {
            pathHandler = deploy(projectId -> new ProducerEndpoint(getMessageStore(projectId, messageType))
                                         .metricsLog(messageType == METRICS ? new NoOpMetricsLog() : metricsLogSupplier.apply(projectId)),
                                 format("/%s/", producerPath(messageType)), pathHandler);
            pathHandler = deploy(projectId -> new ConsumerEndpoint(getMessageStore(projectId, messageType), messageType)
                                         .metricsLog(messageType == METRICS ? new NoOpMetricsLog() : metricsLogSupplier.apply(projectId)),
                                 format("/%s/", consumerPath(messageType)), pathHandler);
        }
        pathHandler = deploy(projectId -> new ConsumerEndpoint(getMessageStore(projectId, NOTIFICATION), NOTIFICATION)
                                     .metricsLog(metricsLogSupplier.apply(projectId)),
                             format("/%s/", consumerPath(NOTIFICATION)), pathHandler);

        for (MessageType messageType : MessageType.values()) {
            switch (messageType) {
                case DOCUMENT, CUSTOM -> pathHandler = deployFromSession(
                        ObjectUtils.<String, String, Endpoint>memoize((projectId, topic) -> new ConsumerEndpoint(
                                getMessageStore(projectId, messageType, topic), messageType)
                                        .metricsLog(metricsLogSupplier.apply(projectId)))
                                .compose(s -> new SimpleEntry<>(getProjectId(s), getTopic(s))),
                        format("/%s/", consumerPath(messageType)), pathHandler);
            }
        }

        pathHandler = deploy(projectId -> new EventSourcingEndpoint(clients.apply(projectId).getEventStoreClient())
                .metricsLog(metricsLogSupplier.apply(projectId)), format("/%s/", eventSourcingPath()), pathHandler);
        pathHandler = deploy(projectId -> new KeyValueEndPoint(clients.apply(projectId).getKeyValueClient())
                .metricsLog(metricsLogSupplier.apply(projectId)), format("/%s/", keyValuePath()), pathHandler);
        pathHandler = deploy(projectId -> new SearchEndpoint(clients.apply(projectId).getSearchClient())
                .metricsLog(metricsLogSupplier.apply(projectId)), format("/%s/", searchPath()), pathHandler);
        pathHandler = deploy(projectId -> new SchedulingEndpoint(clients.apply(projectId).getSchedulingClient())
                .metricsLog(metricsLogSupplier.apply(projectId)), format("/%s/", schedulingPath()), pathHandler);
        pathHandler = deploy(projectId -> new ConsumerEndpoint((MessageStore) clients.apply(projectId).getSchedulingClient(), SCHEDULE)
                                     .metricsLog(metricsLogSupplier.apply(projectId)),
                             format("/%s/", consumerPath(SCHEDULE)), pathHandler);
        pathHandler = pathHandler.addPrefixPath("/health", exchange -> {
            exchange.getResponseHeaders().put(CONTENT_TYPE, "text/plain");
            exchange.getResponseSender().send("Healthy");
        });

        GracefulShutdownHandler shutdownHandler = new GracefulShutdownHandler(pathHandler);

        Undertow server = Undertow.builder().addHttpListener(port, "0.0.0.0").setHandler(shutdownHandler).build();
        server.start();

        getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Initiating controlled shutdown");
            shutdownHandler.shutdown();
            try {
                shutdownHandler.awaitShutdown(1000);
            } catch (InterruptedException e) {
                log.warn("Thread to kill server was interrupted");
                Thread.currentThread().interrupt();
            }
        }, newThreadName("TestServer-shutdown")));

        log.info("Flux Capacitor test server running on port {}", port);
    }

    private static MessageStore getMessageStore(String projectId, MessageType messageType) {
        return getMessageStore(projectId, messageType, null);
    }

    private static MessageStore getMessageStore(String projectId, MessageType messageType, String topic) {
        if (messageType == NOTIFICATION) {
            messageType = EVENT;
        }
        var client = (HasMessageStore) clients.apply(projectId).getTrackingClient(messageType, topic);
        return client.getMessageStore();
    }

    @AllArgsConstructor
    static class TestServerProject implements Client {
        @Delegate
        private final LocalClient delegate;

        @Override
        public SchedulingClient getSchedulingClient() {
            return new TestServerScheduleStore(
                    ((LocalSchedulingClient) delegate.getSchedulingClient()).getMessageStore());
        }
    }

    static String getTopic(Session s) {
        return ofNullable(s.getRequestParameterMap().get("topic")).map(List::getFirst)
                .orElseThrow(() -> new IllegalStateException("Topic parameter missing"));
    }

    static StoreIdentifier getStoreIdentifier(MessageType messageType, Session s) {
        return new StoreIdentifier(
                getProjectId(s), messageType,
                ofNullable(s.getRequestParameterMap().get("topic")).map(List::getFirst)
                        .orElseThrow(() -> new IllegalStateException("Topic parameter missing")));
    }

    @Value
    public static class StoreIdentifier {
        String projectId;
        @With
        MessageType messageType;
        String topic;
    }
}