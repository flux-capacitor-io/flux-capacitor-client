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
import io.fluxcapacitor.common.caching.Cache;
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
import io.fluxcapacitor.javaclient.persisting.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.publishing.CommandGateway;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.publishing.EventGateway;
import io.fluxcapacitor.javaclient.publishing.MetricsGateway;
import io.fluxcapacitor.javaclient.publishing.QueryGateway;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.scheduling.Scheduler;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.ifClass;

@Configuration
@Slf4j
public class FluxCapacitorSpringConfig implements BeanPostProcessor {

    @Bean
    public static TrackSelfPostProcessor trackSelfPostProcessor() {
        return new TrackSelfPostProcessor();
    }

    @Bean
    public static StatefulPostProcessor statefulPostProcessor() {
        return new StatefulPostProcessor();
    }

    private final ApplicationContext context;
    private final List<Object> springBeans = new CopyOnWriteArrayList<>();
    private final AtomicReference<Registration> handlerRegistration = new AtomicReference<>();

    @Autowired
    protected FluxCapacitorSpringConfig(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (ifClass(bean) == null) {
            springBeans.add(bean);
        }
        return bean;
    }

    @EventListener
    public void handle(ContextRefreshedEvent event) {
        FluxCapacitor fluxCapacitor = context.getBean(FluxCapacitor.class);
        List<Object> potentialHandlers = springBeans.stream()
                .map(bean -> bean instanceof FluxPrototype prototype ? prototype.getType() : bean).toList();
        handlerRegistration.updateAndGet(r -> r == null ? fluxCapacitor.registerHandlers(potentialHandlers) : r);
        if (Thread.getDefaultUncaughtExceptionHandler() == null) {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> log.error("Uncaught exception", e));
        }
        if (event.getApplicationContext() instanceof GenericApplicationContext) {
            ((GenericApplicationContext) event.getApplicationContext()).start();
        }
    }

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

    @Bean
    @ConditionalOnMissingBean
    public FluxCapacitor fluxCapacitor(FluxCapacitorBuilder builder, List<FluxCapacitorCustomizer> customizers) {
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
    }

    @Bean
    @ConditionalOnMissingBean
    public AggregateRepository aggregateRepository(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.aggregateRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    public Scheduler scheduler(FluxCapacitor fluxCapacitor) {
        return fluxCapacitor.scheduler();
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
