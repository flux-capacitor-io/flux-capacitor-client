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

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringFluxCapacitorTest.Config.class)
@Slf4j
public class SpringFluxCapacitorTest {

    @Autowired
    private FluxCapacitor fluxCapacitor;

    @Test
    public void testHandleCommand() {
        int result = fluxCapacitor.commandGateway().sendAndWait("test");
        assertEquals(1, result);
    }

    @Component
    public static class SomeHandler {
        @HandleCommand
        public int handleCommand(String command) {
            return 1;
        }
    }

    @Configuration
    @ComponentScan
    public static class Config {
        @Bean
        public SpringFluxCapacitor fluxCapacitor() {
            return SpringFluxCapacitor.builder().build(InMemoryClient.newInstance());
        }
    }
}