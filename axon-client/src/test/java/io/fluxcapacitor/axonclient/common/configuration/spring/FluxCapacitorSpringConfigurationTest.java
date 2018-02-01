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

package io.fluxcapacitor.axonclient.common.configuration.spring;

import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.TargetAggregateIdentifier;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.model.AggregateIdentifier;
import org.axonframework.config.Configuration;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.saga.EndSaga;
import org.axonframework.eventhandling.saga.SagaEventHandler;
import org.axonframework.eventhandling.saga.StartSaga;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.spring.config.EnableAxon;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.spring.stereotype.Saga;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static junit.framework.TestCase.assertEquals;
import static org.axonframework.commandhandling.model.AggregateLifecycle.apply;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@Slf4j
@SuppressWarnings({"SpringJavaAutowiredMembersInspection", "SpringJavaAutowiringInspection", "unused"})
public class FluxCapacitorSpringConfigurationTest {

    @Autowired
    private Configuration configuration;

    @Autowired
    private CountDownLatch eventCountDown;

    @Before
    public void setUp() throws Exception {
        configuration.start();
    }

    @After
    public void tearDown() throws Exception {
        configuration.shutdown();
    }

    @Test
    public void testConfiguration() throws Exception {
        CommandGateway commandGateway = configuration.commandGateway();
        assertEquals(100L, commandGateway.send(100L).get());

        String aggregateId = UUID.randomUUID().toString();
        String testValue = "test1";
        assertEquals(aggregateId, commandGateway.send(new CreateAggregate(aggregateId, testValue)).get());
        assertEquals(testValue, commandGateway.send(new UpdateAggregate(aggregateId, testValue)).get());
        eventCountDown.await();
    }

    @Value
    private static class CreateAggregate {
        @TargetAggregateIdentifier
        String target;
        String value;
    }

    @Value
    private static class UpdateAggregate {
        @TargetAggregateIdentifier
        String target;
        String value;
    }

    @EnableAxon
    @Scope
    @org.springframework.context.annotation.Configuration
    @Import(FluxCapacitorSpringConfiguration.class)
    public static class Context {

        @Aggregate
        @NoArgsConstructor
        public static class MyAggregate {

            @AggregateIdentifier
            private String id;

            @CommandHandler
            public MyAggregate(CreateAggregate command) {
                apply(command);
            }

            @CommandHandler
            public Object handleCommand(UpdateAggregate command) {
                apply(command);
                return command.getValue();
            }

            @EventSourcingHandler
            public void handleEvent(CreateAggregate event) {
                id = event.getTarget();
            }

        }

        @Saga
        public static class MySaga {
            @Autowired
            private CountDownLatch countDown;

            @StartSaga
            @SagaEventHandler(associationProperty = "target")
            public void handle(CreateAggregate event) {
                countDown.countDown();
            }

            @EndSaga
            @SagaEventHandler(associationProperty = "target")
            public void handle(UpdateAggregate event) {
                countDown.countDown();
            }
        }

        @Component
        public static class EventListener {
            @Autowired
            private CountDownLatch countDown;

            @EventHandler
            public void handle(CreateAggregate event) {
                countDown.countDown();
            }

            @EventHandler
            public void handle(UpdateAggregate event) {
                countDown.countDown();
            }
        }

        @Component
        public static class CommandListener {
            @CommandHandler
            public Object handle(Long command) {
                return command;
            }
        }

        @Bean
        public CountDownLatch eventCountDown() {
            return new CountDownLatch(4);
        }
    }

}