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

import com.fasterxml.jackson.databind.node.TextNode;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Upcast;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = FluxCapacitorSpringConfigTest.Config.class)
@Slf4j
public class FluxCapacitorSpringConfigTest {

    @Autowired
    private FluxCapacitor fluxCapacitor;

    @Test
    public void testHandleCommand() {
        String result = fluxCapacitor.commandGateway().sendAndWait("command");
        assertEquals("upcasted result", result);
    }

    @Component
    public static class SomeHandler {
        @HandleCommand
        public Object handleCommand(String command) {
            return "result";
        }
    }

    @Component
    public static class StringUpcaster {
        @Upcast(type = "java.lang.String", revision = 0)
        public TextNode upcastResult(TextNode node) {
            return TextNode.valueOf(node.asText().equals("result") ? "upcasted result" : node.asText());
        }
    }

    @Configuration
    @Import(FluxCapacitorSpringConfig.class)
    @ComponentScan
    public static class Config {

    }
}