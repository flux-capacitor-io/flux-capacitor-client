package io.fluxcapacitor.javaclient.test.spring;


import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.configuration.spring.FluxCapacitorSpringConfig;
import io.fluxcapacitor.javaclient.test.streaming.StreamingTestFixture;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.Optional;

@Configuration
@AllArgsConstructor
@Import(FluxCapacitorSpringConfig.class)
@Slf4j
public class FluxCapacitorTestConfig {

    private final ApplicationContext context;
    
    @Bean
    @Primary
    public FluxCapacitor fluxCapacitor(StreamingTestFixture testFixture) {
        return testFixture.getFluxCapacitor();
    }
    
    @Bean
    public StreamingTestFixture testFixture(FluxCapacitorBuilder fluxCapacitorBuilder) {
        Client client = getBean(Client.class).orElseGet(() -> getBean(WebSocketClient.Properties.class).<Client>map(
                WebSocketClient::newInstance).orElse(null));
        if (client == null) {
            return StreamingTestFixture.create(fluxCapacitorBuilder);
        }
        return StreamingTestFixture.create(fluxCapacitorBuilder, client);
    }

    protected <T> Optional<T> getBean(Class<T> type) {
        return context.getBeansOfType(type).values().stream().findFirst();
    }
    
}
