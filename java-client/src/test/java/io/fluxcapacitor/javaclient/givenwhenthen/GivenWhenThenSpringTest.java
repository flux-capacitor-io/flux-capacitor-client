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

package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.test.spring.FluxCapacitorTestConfig;
import io.fluxcapacitor.javaclient.tracking.Consumer;
import io.fluxcapacitor.javaclient.tracking.TrackSelf;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.SneakyThrows;
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD;

@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = {FluxCapacitorTestConfig.class, GivenWhenThenSpringTest.FooConfig.class, GivenWhenThenSpringTest.BarConfig.class})
class GivenWhenThenSpringTest {

    @Autowired
    private TestFixture testFixture;

    @Test
    void testFoo() {
        testFixture.whenCommand(new DoSomething()).expectEvents(new DoSomething());
    }

    @Test
    void testBar() {
        testFixture.whenCommand(new DoSomething()).expectCommands(new DoSomethingElse());
    }

    @Test
    @SneakyThrows
    void testWaitForSlowResultAfterTerminate() {
        CompletableFuture<Object> result = testFixture.getFluxCapacitor().commandGateway().send(new SlowCommand());
        sleepAWhile(100);
        testFixture.getFluxCapacitor().close();
        assertTrue(result.isDone());
    }

    @Test
    void selfTracked() {
        testFixture.whenCommand(new SelfTracked()).expectEvents(SelfTracked.class);
    }

    @SneakyThrows
    private static void sleepAWhile(int millis) {
        Thread.sleep(millis);
    }

    @Configuration
    @ComponentScan
    static class FooConfig {
        @Bean
        public FooHandler fooHandler() {
            return new FooHandler();
        }
    }

    private static class FooHandler {
        @HandleCommand
        public void handle(DoSomething command) {
            FluxCapacitor.publishEvent(command);
        }

        @HandleCommand
        public CompletableFuture<?> handle(SlowCommand command) {
           return CompletableFuture.runAsync(() -> sleepAWhile(500));
        }
    }

    @Configuration
    static class BarConfig {
        @Bean
        public BarHandler barHandler() {
            return new BarHandler();
        }
    }

    @TrackSelf
    @Consumer(name = "SelfTracked")
    public static class SelfTracked {
        @HandleCommand
        void handleSelf() {
            if (Tracker.current().isPresent()
                && "SelfTracked".equals(Tracker.current().get().getConfiguration().getName())) {
                FluxCapacitor.publishEvent(this);
            }
        }
    }

    static class BarHandler {
        @HandleEvent
        public void handle(DoSomething event) {
            FluxCapacitor.sendAndForgetCommand(new DoSomethingElse());
        }
    }

    @Value
    private static class DoSomething {
    }

    @Value
    private static class DoSomethingElse {
    }

    @Value
    private static class SlowCommand {
    }

}