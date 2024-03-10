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

import io.undertow.Undertow;
import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.websockets.jsr.WebSocketDeploymentInfo;
import jakarta.websocket.Endpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import lombok.SneakyThrows;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

import java.util.function.Function;

import static io.undertow.servlet.Servlets.deployment;

public class WebsocketDeploymentUtils {

    private static final ByteBufferPool bufferPool =
            new DefaultByteBufferPool(false, 1024, 100, 12);

    @SneakyThrows
    public static PathHandler deploy(Function<String, Endpoint> endpointSupplier, String path, PathHandler pathHandler) {
        ServerEndpointConfig config = ServerEndpointConfig.Builder
                .create(MultiClientEndpoint.class, "/")
                .configurator(
                        new ServerEndpointConfig.Configurator() {
                            final MultiClientEndpoint endpoint = new MultiClientEndpoint(endpointSupplier);

                            @Override
                            public <T> T getEndpointInstance(Class<T> endpointClass) {
                                return endpointClass.cast(endpoint);
                            }
                        }
                )
                .build();
        DeploymentManager deploymentManager = Servlets.defaultContainer()
                .addDeployment(deployment()
                                       .setContextPath("/")
                                       .addServletContextAttribute(WebSocketDeploymentInfo.ATTRIBUTE_NAME,
                                                                   createWebsocketDeploymentInfo()
                                                                           .addEndpoint(config))
                                       .setDeploymentName(path)
                                       .setClassLoader(Undertow.class.getClassLoader()));
        deploymentManager.deploy();
        return pathHandler.addPrefixPath(path, deploymentManager.start());
    }

    public static WebSocketDeploymentInfo createWebsocketDeploymentInfo() {
        return new WebSocketDeploymentInfo().setBuffers(bufferPool).setWorker(createWorker());
    }

    @SneakyThrows
    private static XnioWorker createWorker() {
        return Xnio.getInstance().createWorker(OptionMap.create(Options.THREAD_DAEMON, true));
    }
}
