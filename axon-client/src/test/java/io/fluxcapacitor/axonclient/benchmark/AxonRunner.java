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

package io.fluxcapacitor.axonclient.benchmark;

import io.fluxcapacitor.axonclient.common.configuration.WebsocketFluxCapacitorConfiguration;
import io.fluxcapacitor.javaclient.benchmark.AbstractClientBenchmark;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.serialization.json.JacksonSerializer;

@Slf4j
public class AxonRunner extends AbstractClientBenchmark {

    public static void main(final String[] args) throws Exception {
        AxonRunner runner = new AxonRunner(100_000);
        runner.configuration.start();
        runner.testCommands();
        runner.configuration.shutdown();
        System.exit(0);
    }

    private final Configuration configuration;
    private final CommandBus commandBus;

    public AxonRunner(int commandCount) {
        super(commandCount);

        Configurer configurer = DefaultConfigurer
                .defaultConfiguration()
                .configureSerializer(c -> new JacksonSerializer())
                .registerCommandHandler(c -> this);
        WebsocketFluxCapacitorConfiguration.configure(configurer, getClientProperties());

        this.configuration = configurer.buildConfiguration();
        this.commandBus = configuration.commandBus();
    }

    @Override
    protected void doSendCommand(String payload) {
        commandBus.dispatch(GenericCommandMessage.asCommandMessage(payload));
    }

    @CommandHandler
    public void handle(String command) {
        getCommandCountDownLatch().countDown();
    }

}
