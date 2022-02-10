package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;

public class AggregateEntitiesTest {

    @Nested
    class FindEntityTests {
        @Test
        void findSingleton() {
            TestFixture.create()
                    .whenApplying(fc -> loadAggregate("test", Aggregate.class, false)
                            .update(s -> new Aggregate()).entities())
                    .<Map<String, Entity<?, ?>>>expectResult(entities -> entities.containsKey("singleton"));
        }

        @Value
        class Aggregate {
            @Member
            EntityWithAnnotatedId singleton = new EntityWithAnnotatedId("singleton");
        }

        @Value
        class EntityWithAnnotatedId {
            @EntityId
            String id;
        }
    }

}
