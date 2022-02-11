package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.test.TestFixture;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.ObjectUtils.safelyCall;
import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;

@SuppressWarnings({"rawtypes", "SameParameterValue", "unchecked"})
public class AggregateEntitiesTest {

    @Nested
    class FindEntityTests {
        @Test
        void findSingleton() {
            expectEntity(Aggregate.class, e -> "id".equals(e.id()) && "id".equals(e.idProperty()));
        }

        @Test
        void findSingletonWithCustomPath() {
            expectEntity(Aggregate.class, e -> "otherId".equals(e.id()) && "customId".equals(e.idProperty()));
        }

        @Test
        void noEntityIfNull() {
            expectNoEntity(Aggregate.class, e -> "missingId".equals(e.id()));
        }

        @Test
        void findEntitiesInList() {
            expectEntity(Aggregate.class, e -> "list0".equals(e.id()));
            expectEntity(Aggregate.class, e -> "list1".equals(e.id()));
            expectEntity(Aggregate.class, e -> e.id() == null);
        }

        @Test
        void findEntitiesInMapUsingKey() {
            expectEntity(Aggregate.class, e -> "map0".equals(e.id()));
            expectEntity(Aggregate.class, e -> "map1".equals(e.id()));
        }
    }

    @Value
    static class Aggregate {
        @Member
        EntityWithAnnotatedId singleton = new EntityWithAnnotatedId();

        @Member(idProperty = "customId")
        EntityWithAnnotatedId singletonCustomPath = new EntityWithAnnotatedId();

        @Member(idProperty = "missingId")
        EntityWithAnnotatedId missingSingleton = null;

        @Member(idProperty = "collectionId")
        List<EntityWithAnnotatedId> list = List.of(
                new EntityWithAnnotatedId("list0"), new EntityWithAnnotatedId("list1"), new EntityWithAnnotatedId());

        @Member
        Map<?, EntityWithAnnotatedId> map = Map.of(
                "map0", new EntityWithAnnotatedId(), new Key("map1"), new EntityWithAnnotatedId());
    }

    @Value
    @RequiredArgsConstructor
    @AllArgsConstructor
    static class EntityWithAnnotatedId {
        @EntityId
        String id = "id";
        String customId = "otherId";
        String missingId = "missingId";
        @NonFinal
        String collectionId;
    }


    void expectEntity(Class<?> parentClass, Predicate<Entity> predicate) {
        expectEntities(parentClass, entities -> entities.stream().anyMatch(predicate));
    }

    void expectNoEntity(Class<?> parentClass, Predicate<Entity> predicate) {
        expectEntities(parentClass, entities -> entities.stream().noneMatch(predicate));
    }

    void expectEntities(Class<?> parentClass, Predicate<Collection<Entity>> predicate) {
        TestFixture.create()
                .whenApplying(fc -> loadAggregate("test", (Class) parentClass, false)
                        .update(s -> safelyCall(() -> parentClass.getConstructor().newInstance())).entities())
                .expectResult(predicate);
    }

    @AllArgsConstructor
    static class Key {
        String key;

        @Override
        public String toString() {
            return key;
        }
    }
}
