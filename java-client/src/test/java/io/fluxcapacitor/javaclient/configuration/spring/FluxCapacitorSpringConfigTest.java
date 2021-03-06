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

package io.fluxcapacitor.javaclient.configuration.spring;

import com.fasterxml.jackson.databind.node.TextNode;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Upcast;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.persisting.caching.DefaultCache;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import io.fluxcapacitor.javaclient.tracking.handling.LocalHandler;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = FluxCapacitorSpringConfigTest.Config.class)
@Slf4j
public class FluxCapacitorSpringConfigTest {
    private static final User mockUser = mock(User.class);
    private static final UserProvider mockUserProvider = mock(UserProvider.class);

    static {
        when(mockUserProvider.fromMetadata(any(Metadata.class))).thenReturn(mockUser);
    }

    @BeforeAll
    static void beforeAll() {
        System.setProperty("existingProperty", "test");
    }

    @Autowired
    private FluxCapacitor fluxCapacitor;

    @Autowired
    BeanFactory beanFactory;

    @Test
    void testHandleCommand() {
        String result = fluxCapacitor.commandGateway().sendAndWait("command");
        assertEquals("upcasted result", result);
    }

    @Test
    void testUserProviderInjected() {
        assertEquals(mockUser, fluxCapacitor.queryGateway().sendAndWait(new GetUser()));
    }

    @Test
    void testDefaultCacheReplaced() {
        assertTrue(fluxCapacitor.cache() instanceof Config.CustomCache);
    }

    @Test
    void testConditionalComponentMissing() {
        assertThrows(NoSuchBeanDefinitionException.class, () -> beanFactory.getBean(ConditionalComponentMissing.class));
    }

    @Test
    void testConditionalComponentPresentIfPropertyExists() {
        assertNotNull(beanFactory.getBean(ConditionalComponentPresent.class));
    }

    @Component
    public static class SomeHandler {
        @HandleCommand
        public Object handleCommand(String command, User user) {
            assertNotNull(user);
            return "result";
        }
    }

    @Component @LocalHandler
    public static class SomeLocalHandler {
        @HandleQuery
        public User handle(GetUser query) {
            return User.getCurrent();
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

        @Autowired
        void configure(FluxCapacitorBuilder builder) {
            builder.makeApplicationInstance(false);
        }

        @Bean
        public UserProvider userProvider() {
            return mockUserProvider;
        }

        @Bean
        public CustomCache cache() {
            return new CustomCache();
        }

        private static class CustomCache extends DefaultCache {
        }
    }

    @Value
    static class GetUser {
    }

    @Value
    @ConditionalOnProperty("missingProperty")
    @Component
    public static class ConditionalComponentMissing {
    }

    @Value
    @ConditionalOnProperty("existingProperty")
    @Component
    public static class ConditionalComponentPresent {
    }

}
