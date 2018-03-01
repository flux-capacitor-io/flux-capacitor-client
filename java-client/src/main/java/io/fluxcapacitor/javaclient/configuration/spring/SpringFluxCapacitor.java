/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.configuration.spring;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.eventsourcing.EventSourcing;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.publishing.CommandGateway;
import io.fluxcapacitor.javaclient.publishing.EventGateway;
import io.fluxcapacitor.javaclient.publishing.QueryGateway;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class SpringFluxCapacitor extends DefaultFluxCapacitor implements BeanPostProcessor {

    private final List<Object> springBeans = new CopyOnWriteArrayList<>();
    private final AtomicReference<Registration> trackingRegistration = new AtomicReference<>();

    protected SpringFluxCapacitor(
            Map<MessageType, Tracking> trackingSupplier,
            CommandGateway commandGateway, QueryGateway queryGateway,
            EventGateway eventGateway, ResultGateway resultGateway,
            EventSourcing eventSourcing, KeyValueStore keyValueStore,
            Scheduler scheduler) {
        super(trackingSupplier, commandGateway, queryGateway, eventGateway, resultGateway, eventSourcing, keyValueStore,
              scheduler);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        springBeans.add(bean);
        return bean;
    }

    @EventListener
    public void handle(ContextRefreshedEvent event) {
        trackingRegistration.updateAndGet(r -> r == null ? startTracking(springBeans.toArray()) : r);
    }

    @EventListener
    public void handle(ContextClosedEvent event) {
        trackingRegistration.getAndUpdate(r -> null).cancel();
    }

    public static class Builder extends DefaultFluxCapacitor.Builder {
        @Override
        public SpringFluxCapacitor build(Client client) {
            return (SpringFluxCapacitor) super.build(client);
        }

        @Override
        protected SpringFluxCapacitor doBuild(Map<MessageType, Tracking> trackingSupplier, CommandGateway commandGateway,
                                        QueryGateway queryGateway, EventGateway eventGateway,
                                        ResultGateway resultGateway,
                                        EventSourcing eventSourcing, KeyValueStore keyValueStore, Scheduler scheduler) {
            return new SpringFluxCapacitor(trackingSupplier, commandGateway, queryGateway, eventGateway, resultGateway,
                                           eventSourcing, keyValueStore, scheduler);
        }
    }

}
