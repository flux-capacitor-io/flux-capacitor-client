package io.fluxcapacitor.javaclient.configuration.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.UpcastInspector;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
@Slf4j
public class FluxCapacitorSpringConfig implements BeanPostProcessor {

    private final ApplicationContext context;
    private final List<Object> springBeans = new CopyOnWriteArrayList<>();
    private final AtomicReference<Registration> handlerRegistration = new AtomicReference<>();

    @Autowired
    protected FluxCapacitorSpringConfig(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        springBeans.add(bean);
        return bean;
    }

    @EventListener
    public void handle(ContextRefreshedEvent event) {
        FluxCapacitor fluxCapacitor = context.getBean(FluxCapacitor.class);
        handlerRegistration.updateAndGet(r -> r == null ? fluxCapacitor.registerHandlers(springBeans) : r);
    }

    @Bean
    @ConditionalOnMissingBean
    public Serializer serializer() {
        List<Object> upcasters = new ArrayList<>();
        for (String beanName : context.getBeanDefinitionNames()) {
            Optional.ofNullable(context.getType(beanName)).filter(UpcastInspector::hasAnnotatedMethods)
                    .ifPresent(t -> upcasters.add(context.getAutowireCapableBeanFactory().getBean(beanName)));
        }
        return getBean(ObjectMapper.class).map(objectMapper -> new JacksonSerializer(objectMapper, upcasters))
                .orElse(new JacksonSerializer(upcasters));
    }

    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public FluxCapacitorBuilder fluxCapacitorBuilder(Serializer serializer, Optional<UserProvider> userProvider) {
        FluxCapacitorBuilder builder = DefaultFluxCapacitor.builder().disableShutdownHook()
                .replaceSerializer(serializer).replaceSnapshotSerializer(serializer).makeApplicationInstance();
        userProvider.ifPresent(builder::registerUserSupplier);
        return builder;
    }

    @Bean
    @ConditionalOnMissingBean
    public FluxCapacitor fluxCapacitor(FluxCapacitorBuilder builder) {
        Client client = getBean(Client.class).orElseGet(() -> getBean(WebSocketClient.Properties.class).<Client>map(
                WebSocketClient::newInstance).orElseGet(() -> {
            log.info("Using in-memory Flux Capacitor client");
            return InMemoryClient.newInstance();
        }));
        return builder.build(client);
    }

    protected <T> Optional<T> getBean(Class<T> type) {
        return context.getBeansOfType(type).values().stream().findFirst();
    }

}
