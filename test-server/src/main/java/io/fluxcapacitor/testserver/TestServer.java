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

package io.fluxcapacitor.testserver;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;
import io.fluxcapacitor.javaclient.scheduling.client.InMemorySchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;
import io.fluxcapacitor.testserver.endpoints.ConsumerEndpoint;
import io.fluxcapacitor.testserver.endpoints.EventSourcingEndpoint;
import io.fluxcapacitor.testserver.endpoints.KeyValueEndPoint;
import io.fluxcapacitor.testserver.endpoints.ProducerEndpoint;
import io.fluxcapacitor.testserver.endpoints.SchedulingEndpoint;
import io.fluxcapacitor.testserver.endpoints.SearchEndpoint;
import io.undertow.Undertow;
import io.undertow.server.handlers.GracefulShutdownHandler;
import io.undertow.server.handlers.PathHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
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
import static io.fluxcapacitor.common.ServicePathBuilder.consumerPath;
import static io.fluxcapacitor.common.ServicePathBuilder.eventSourcingPath;
import static io.fluxcapacitor.common.ServicePathBuilder.keyValuePath;
import static io.fluxcapacitor.common.ServicePathBuilder.producerPath;
import static io.fluxcapacitor.common.ServicePathBuilder.schedulingPath;
import static io.fluxcapacitor.common.ServicePathBuilder.searchPath;
import static io.fluxcapacitor.testserver.WebsocketDeploymentUtils.deploy;
import static io.undertow.Handlers.path;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static java.lang.Runtime.getRuntime;
import static java.lang.String.format;

@Slf4j
public class TestServer {
    private static final Function<String, InMemoryClient> clients = memoize(projectId -> InMemoryClient.newInstance());

    public static void main(final String[] args) {
        start(Integer.getInteger("port", 8080));
    }

    public static void start(int port) {
        PathHandler pathHandler = path();
        for (MessageType messageType : Arrays.asList(METRICS, EVENT, COMMAND, QUERY, RESULT, ERROR, WEBREQUEST, WEBRESPONSE)) {
            pathHandler = deploy(projectId -> new ProducerEndpoint(getMessageStore(projectId, messageType)),
                                 format("/%s/", producerPath(messageType)), pathHandler);
            pathHandler = deploy(projectId -> new ConsumerEndpoint(getMessageStore(projectId, messageType), messageType),
                                 format("/%s/", consumerPath(messageType)), pathHandler);
        }
        pathHandler = deploy(projectId -> new ConsumerEndpoint(getMessageStore(projectId, NOTIFICATION), NOTIFICATION),
                             format("/%s/", consumerPath(NOTIFICATION)), pathHandler);
        pathHandler = deploy(projectId -> new EventSourcingEndpoint(clients.apply(projectId).getEventStoreClient()), format("/%s/", eventSourcingPath()), pathHandler);
        pathHandler = deploy(projectId -> new KeyValueEndPoint(clients.apply(projectId).getKeyValueClient()), format("/%s/", keyValuePath()), pathHandler);
        pathHandler = deploy(projectId -> new SearchEndpoint(clients.apply(projectId).getSearchClient()), format("/%s/", searchPath()), pathHandler);
        pathHandler = deploy(projectId -> new SchedulingEndpoint(clients.apply(projectId).getSchedulingClient()), format("/%s/", schedulingPath()), pathHandler);
        pathHandler = deploy(projectId -> new ConsumerEndpoint((InMemorySchedulingClient) clients.apply(projectId).getSchedulingClient(), SCHEDULE),
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
        }));

        log.info("Flux Capacitor test server running on port {}", port);
    }

    private static InMemoryMessageStore getMessageStore(String projectId, MessageType messageType) {
        if (messageType == NOTIFICATION) {
            messageType = EVENT;
        }
        return (InMemoryMessageStore) clients.apply(projectId).getGatewayClient(messageType);
    }

}