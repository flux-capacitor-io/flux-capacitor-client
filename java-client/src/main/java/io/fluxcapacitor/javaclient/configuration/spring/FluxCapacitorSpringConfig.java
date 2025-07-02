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

package io.fluxcapacitor.javaclient.configuration.spring;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.casting.CastInspector;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.ApplicationProperties;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.LocalClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.publishing.CommandGateway;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.publishing.EventGateway;
import io.fluxcapacitor.javaclient.publishing.MetricsGateway;
import io.fluxcapacitor.javaclient.publishing.QueryGateway;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.scheduling.MessageScheduler;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import io.fluxcapacitor.javaclient.web.WebResponseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.ifClass;

/**
 * Spring configuration class for automatically wiring and initializing common Flux Capacitor components in a Spring
 * application context.
 * <p>
 * This configuration simplifies the integration of Flux Capacitor by:
 * <ul>
 *     <li>Registering {@code @Handle...} annotated beans as handlers after the context is refreshed</li>
 *     <li>Auto-detecting and registering upcasters and downcasters with the {@link Serializer}</li>
 *     <li>Providing default implementations for core interfaces like {@link CommandGateway}, {@link Serializer}, and {@link MessageScheduler}</li>
 * </ul>
 * <p>
 * Note that Flux Capacitor does not require Spring, and this class is entirely optional.
 * It exists purely to reduce boilerplate in Spring-based applications.
 *
 * <p>The simplest way to enable this configuration in a Spring Boot application, is by annotating your main application
 * class with:
 * <pre>{@code
 * @SpringBootApplication
 * @Import(FluxCapacitorSpringConfig.class)
 * }</pre>
 *
 * @see FluxCapacitor
 * @see FluxCapacitorBuilder
 */
@Configuration
@Slf4j
public class FluxCapacitorSpringConfig implements BeanPostProcessor {

    /**
     * Registers the {@link TrackSelfPostProcessor}, which supports payload classes that track and handle their own
     * type.
     *
     * @see io.fluxcapacitor.javaclient.tracking.TrackSelf
     */
    @Bean
    public static TrackSelfPostProcessor trackSelfPostProcessor() {
        return new TrackSelfPostProcessor();
    }

    /**
     * Registers the {@link StatefulPostProcessor}, enabling lifecycle and stateful behavior for beans.
     *
     * @see io.fluxcapacitor.javaclient.tracking.handling.Stateful
     */
    @Bean
    public static StatefulPostProcessor statefulPostProcessor() {
        return new StatefulPostProcessor();
    }

    /**
     * Registers the {@link SocketEndpointPostProcessor}, used for handlers that manage WebSocket communication.
     *
     * @see io.fluxcapacitor.javaclient.web.SocketEndpoint
     */
    @Bean
    public static SocketEndpointPostProcessor socketEndpointPostProcessor() {
        return new SocketEndpointPostProcessor();
    }

    private final ApplicationContext context;
    private final Set<Object> springBeans = new CopyOnWriteArraySet<>();
    private final AtomicReference<Registration> handlerRegistration = new AtomicReference<>();

    /**
     * Stores a reference to the Spring context and prepares for handler detection.
     */
    @Autowired
    protected FluxCapacitorSpringConfig(ApplicationContext context) {
        this.context = context;
    }

    /**
     * Captures beans post-initialization to inspect later for handler registration. Prototype beans are handled
     * specially.
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (ifClass(bean) == null) {
            springBeans.add(bean);
        }
        return bean;
    }

    /**
     * Registers all discovered Spring beans as Flux Capacitor handlers once the application context is refreshed. Also
     * installs a default uncaught exception handler and starts the application context if needed.
     */
    @EventListener
    public void handle(ContextRefreshedEvent event) {
        FluxCapacitor fluxCapacitor = context.getBean(FluxCapacitor.class);
        Optional<SpringHandlerRegistry> handlerRegistry = getBean(SpringHandlerRegistry.class);
        List<Object> potentialHandlers = springBeans.stream()
                .map(bean -> bean instanceof FluxPrototype prototype ? prototype.getType() : bean).toList();
        handlerRegistration.updateAndGet(r -> r == null ?
                handlerRegistry.map(hr -> hr.registerHandlers(potentialHandlers))
                        .orElseGet(() -> fluxCapacitor.registerHandlers(potentialHandlers)) : r);
        if (Thread.getDefaultUncaughtExceptionHandler() == null) {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception", e));
        }
        if (event.getApplicationContext() instanceof GenericApplicationContext) {
            ((GenericApplicationContext) event.getApplicationContext()).start();
        }
    }

    /**
     * Optionally provides a default {@link Serializer} implementation based on Jackson, automatically detecting and
     * registering upcasters and downcasters from Spring-managed beans.
     * <p>
     * This method is only invoked if no other {@link Serializer} bean is defined.
     * <p>
     * If a custom serializer is provided by the application, upcasters and downcasters must be registered explicitly.
     *
     * @return a {@link Serializer} configured with discovered casting logic
     */
    @Bean
    @ConditionalOnMissingBean
    public Serializer serializer() {
        List<Object> upcasters = new ArrayList<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            Optional.ofNullable(context.getType(beanName)).filter(CastInspector::hasCasterMethods)
                    .ifPresent(t -> upcasters.add(context.getAutowireCapableBeanFactory().getBean(beanName)));
        }
        return new JacksonSerializer(upcasters);
    }

    /**
     * Provides a default {@link FluxCapacitorBuilder}, configured using Spring-provided components such as
     * {@link UserProvider}, {@link Cache}, and {@link WebResponseMapper}. Automatically uses application properties via
     * {@link SpringPropertySource}.
     */
    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public FluxCapacitorBuilder fluxCapacitorBuilder(
            Serializer serializer, Optional<UserProvider> userProvider, Optional<Cache> cache,
            Optional<WebResponseMapper> webResponseMapper, Environment environment) {
        FluxCapacitorBuilder builder = DefaultFluxCapacitor.builder().disableShutdownHook()
                .replaceSerializer(serializer).replaceSnapshotSerializer(serializer).makeApplicationInstance(true);
        userProvider.ifPresent(builder::registerUserProvider);
        cache.ifPresent(builder::replaceCache);
        webResponseMapper.ifPresent(builder::replaceWebResponseMapper);
        builder.addPropertySource(new SpringPropertySource(environment));
        builder.addParameterResolver(new SpringBeanParameterResolver(context));
        return builder;
    }

    /**
     * Constructs the {@link FluxCapacitor} instance if no FluxCapacitor bean exists, preferring a user-provided
     * {@link Client} or falling back to either a {@link WebSocketClient} or {@link LocalClient} depending on presence
     * of configuration properties.
     */
    @Bean
    @ConditionalOnMissingBean
    public FluxCapacitor fluxCapacitor(FluxCapacitorBuilder builder, List<FluxCapacitorCustomizer> customizers) {
        return getBean(FluxCapacitor.class).orElseGet(() -> {
            Client client = getBean(Client.class).orElseGet(() -> getBean(WebSocketClient.ClientConfig.class)
                    .<Client>map(WebSocketClient::newInstance)
                    .orElseGet(() -> {
                        if (ApplicationProperties.containsProperty("FLUX_BASE_URL")
                            && ApplicationProperties.containsProperty("FLUX_APPLICATION_NAME")) {
                            var config = WebSocketClient.ClientConfig.builder().build();
                            log.info("Using connected Flux Capacitor client (application name: {}, service url: {})",
                                     config.getName(), config.getServiceBaseUrl());
                            return WebSocketClient.newInstance(config);
                        }
                        log.info("Using in-memory Flux Capacitor client");
                        return LocalClient.newInstance();
                    }));

            FluxCapacitorCustomizer customizer = customizers.stream()
                    .reduce((first, second) -> b -> second.customize(first.customize(b)))
                    .orElse(b -> b);

            return customizer.customize(builder).build(client);
        });
    }

    @Bean
    @ConditionalOnMissingBean
    public AggregateRepository aggregateRepository(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.aggregateRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageScheduler scheduler(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.messageScheduler();
    }

    @Bean
    @ConditionalOnMissingBean
    public CommandGateway commandGateway(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.commandGateway();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventGateway eventGateway(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.eventGateway();
    }

    @Bean
    @ConditionalOnMissingBean
    public QueryGateway queryGateway(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.queryGateway();
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorGateway errorGateway(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.errorGateway();
    }

    @Bean
    @ConditionalOnMissingBean
    public MetricsGateway metricsGateway(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.metricsGateway();
    }

    @Bean
    @ConditionalOnMissingBean
    public ResultGateway resultGateway(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.resultGateway();
    }

    @Bean
    @ConditionalOnMissingBean
    public KeyValueStore keyValueStore(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.keyValueStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentStore documentStore(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.documentStore();
    }

    protected <T> Optional<T> getBean(Class<T> type) {
        return context.getBeansOfType(type).values().stream().findFirst();
    }
}
