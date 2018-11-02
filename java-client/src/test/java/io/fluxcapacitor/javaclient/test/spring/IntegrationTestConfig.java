package io.fluxcapacitor.javaclient.test.spring;


import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.spring.FluxCapacitorSpringConfig;
import io.fluxcapacitor.javaclient.test.streaming.StreamingTestFixture;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Configuration
@Import(FluxCapacitorSpringConfig.class)
public class IntegrationTestConfig {
    
    @Bean
    @Primary
    public FluxCapacitor fluxCapacitor(StreamingTestFixture testFixture) {
        return testFixture.getFluxCapacitor();
    }
    
    @Bean
    public StreamingTestFixture testFixture(FluxCapacitorBuilder fluxCapacitorBuilder) {
        return StreamingTestFixture.create(fluxCapacitorBuilder);
    }
    
}
