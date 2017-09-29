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

package io.fluxcapacitor.axonclient.common.configuration;

import io.fluxcapacitor.axonclient.commandhandling.CommandProcessor;
import io.fluxcapacitor.axonclient.commandhandling.FluxCapacitorCommandBus;
import io.fluxcapacitor.axonclient.commandhandling.result.ResultProcessor;
import io.fluxcapacitor.axonclient.common.serialization.AxonMessageSerializer;
import io.fluxcapacitor.axonclient.common.serialization.DefaultAxonMessageSerializer;
import io.fluxcapacitor.axonclient.eventhandling.FluxCapacitorEventProcessor;
import io.fluxcapacitor.axonclient.eventhandling.FluxCapacitorEventStore;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.common.connection.ApplicationProperties;
import io.fluxcapacitor.javaclient.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.tracking.ConsumerService;
import io.fluxcapacitor.javaclient.tracking.ProducerService;
import org.axonframework.commandhandling.SimpleCommandBus;
import org.axonframework.commandhandling.distributed.AnnotationRoutingStrategy;
import org.axonframework.commandhandling.distributed.RoutingStrategy;
import org.axonframework.commandhandling.distributed.UnresolvedRoutingKeyPolicy;
import org.axonframework.config.*;
import org.axonframework.eventhandling.*;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;

public abstract class AbstractFluxCapacitorConfiguration implements FluxCapacitorConfiguration {

    private final ApplicationProperties applicationProperties;
    private final AtomicReference<ConsumerService> eventConsumerService = new AtomicReference<>();

    protected AbstractFluxCapacitorConfiguration(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Override
    public Configurer configure(Configurer configurer) {
        SimpleCommandBus localCommandBus = new SimpleCommandBus();
        configurer = configurer
                .registerComponent(AxonMessageSerializer.class, c -> new DefaultAxonMessageSerializer(
                        c.serializer(), c.getComponent(EventUpcasterChain.class, EventUpcasterChain::new)))
                .registerComponent(ResultProcessor.class,
                                   c -> new ResultProcessor(c.getComponent(AxonMessageSerializer.class),
                                                            createConsumerService(MessageType.RESULT),
                                                            format("%s/result", applicationProperties.getApplicationName())))
                .registerComponent(CommandProcessor.class,
                                   c -> new CommandProcessor(
                                           c.getComponent(AxonMessageSerializer.class),
                                           localCommandBus, createProducerService(MessageType.RESULT),
                                           format("%s/command", applicationProperties.getApplicationName()),
                                           createConsumerService(MessageType.COMMAND)))
                .registerComponent(RoutingStrategy.class,
                                   c -> new AnnotationRoutingStrategy(UnresolvedRoutingKeyPolicy.RANDOM_KEY))
                .configureCommandBus(c -> new FluxCapacitorCommandBus(
                        createProducerService(MessageType.COMMAND), c.getComponent(ResultProcessor.class),
                        c.getComponent(AxonMessageSerializer.class),
                        c.getComponent(RoutingStrategy.class), applicationProperties.getClientId(), localCommandBus))
                .registerComponent(FluxCapacitorEventStore.class, this::createEventStore)
                .configureEventStore(c -> c.getComponent(FluxCapacitorEventStore.class))
                .registerModule(new FluxCapacitorModuleConfiguration());
        configurer = configureEventHandling(configurer);
        configurer = configureSagaManagers(configurer);
        return configurer;
    }

    @Override
    public EventHandlingConfiguration configure(EventHandlingConfiguration eventHandlingConfiguration) {
        return eventHandlingConfiguration.registerEventProcessorFactory(
                (c, name, eventListeners) -> {
                    String processorName = format("%s/%s", applicationProperties.getApplicationName(), name);
                    return new FluxCapacitorEventProcessor(
                            processorName,
                            new SimpleEventHandlerInvoker(eventListeners, c.parameterResolverFactory(),
                                                          c.getComponent(ListenerInvocationErrorHandler.class,
                                                                         LoggingErrorHandler::new)),
                            RollbackConfigurationType.ANY_THROWABLE, PropagatingErrorHandler.INSTANCE,
                            c.messageMonitor(FluxCapacitorEventProcessor.class, processorName),
                            getEventConsumerService(), c.getComponent(AxonMessageSerializer.class), 1);
                });
    }

    protected Configurer configureEventHandling(Configurer configurer) {
        Configuration configuration = ReflectionUtils.getField("config", configurer);
        configuration.getModules().forEach(m -> {
            if (m instanceof EventHandlingConfiguration) {
                configure((EventHandlingConfiguration) m);
            }
        });
        return configurer;
    }

    protected Configurer configureSagaManagers(Configurer configurer) {
        Configuration configuration = ReflectionUtils.getField("config", configurer);
        configuration.getModules().forEach(m -> {
            if (m instanceof SagaConfiguration) {
                SagaConfiguration sagaConfig = (SagaConfiguration) m;
                Component<EventProcessor> processorComponent = ReflectionUtils.getField("processor", sagaConfig);
                String name = ReflectionUtils.getField("name", processorComponent);
                processorComponent.update(c -> {
                    String processorName = format("%s/%s", applicationProperties.getApplicationName(), name);
                    Logger logger = LoggerFactory.getLogger(processorName);
                    return new FluxCapacitorEventProcessor(
                            processorName, sagaConfig.getSagaManager(),
                            RollbackConfigurationType.ANY_THROWABLE,
                            errorContext -> logger.error("Failed to handle events on saga", errorContext.error()),
                            c.messageMonitor(FluxCapacitorEventProcessor.class, processorName), getEventConsumerService(),
                            c.getComponent(AxonMessageSerializer.class), 1);
                });
            }
        });
        return configurer;
    }

    protected abstract ConsumerService createConsumerService(MessageType type);

    protected abstract ProducerService createProducerService(MessageType type);

    protected abstract EventStore createEventStore();

    protected ApplicationProperties getApplicationProperties() {
        return applicationProperties;
    }

    private ConsumerService getEventConsumerService() {
        return eventConsumerService
                .updateAndGet(service -> service == null ? createConsumerService(MessageType.EVENT) : service);
    }

    @SuppressWarnings("unchecked")
    protected FluxCapacitorEventStore createEventStore(Configuration configuration) {
        MessageMonitor monitor = configuration.messageMonitor(FluxCapacitorEventStore.class, "eventStore");
        EventStore delegate = createEventStore();
        return new FluxCapacitorEventStore(monitor, delegate, configuration.getComponent(AxonMessageSerializer.class));
    }

    protected static class FluxCapacitorModuleConfiguration implements ModuleConfiguration {

        private Configuration config;

        @Override
        public void initialize(Configuration config) {
            this.config = config;
        }

        @Override
        public void start() {
            config.getComponent(ResultProcessor.class).start();
            config.getComponent(CommandProcessor.class).start();
        }

        @Override
        public void shutdown() {
            config.getComponent(CommandProcessor.class).shutDown();
            config.getComponent(ResultProcessor.class).shutDown();
        }
    }
}