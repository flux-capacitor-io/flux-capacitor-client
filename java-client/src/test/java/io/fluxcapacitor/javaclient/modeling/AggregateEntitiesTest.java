package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.IllegalCommandException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
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

        @Test
        void findGrandChild() {
            expectEntity(Aggregate.class, e -> e.entities().stream().findFirst().map(c -> "grandChild".equals(c.id())).orElse(false));
        }
    }

    @Nested
    class AssertLegalTests {
        private final TestFixture testFixture = (TestFixture) TestFixture.create(new CommandHandler())
                .given(fc -> loadAggregate("test", Aggregate.class, false)
                        .update(s -> safelyCall(() -> Aggregate.class.getConstructor().newInstance())));

        @Test
        void testRouteToChild() {
            testFixture.whenCommand(new CommandWithRoutingKey("id"))
                    .expectException(IllegalCommandException.class);
        }

        @Test
        void testRouteToGrandchild() {
            testFixture.whenCommand(new CommandWithRoutingKey("grandChild"))
                    .expectException(IllegalCommandException.class);
        }

        @Test
        void testNoChildRoute() {
            testFixture.whenCommand(new CommandWithRoutingKey("somethingRandom")).expectNoException();
        }

        class CommandHandler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate("test", Aggregate.class).assertLegal(command);
            }
        }
    }

    @Value
    static class Aggregate {
        @Member
        Child singleton = new Child();

        @Member(idProperty = "customId")
        Child singletonCustomPath = new Child();

        @Member(idProperty = "missingId")
        Child missingSingleton = null;

        @Member(idProperty = "customId")
        List<Child> list = List.of(
                Child.builder().customId("list0").build(),
                Child.builder().customId("list1").build(), Child.builder().customId(null).build());

        @Member
        Map<?, Child> map = Map.of(
                "map0", new Child(), new Key("map1"), new Child());

        @Member(idProperty = "customId")
        Child childWithGrandChild = Child.builder().customId("withChild").child(Child.builder().customId("grandChild").build()).build();
    }

    @Value
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class Child {
        @EntityId
        String id = "id";
        @NonFinal
        String customId = "otherId";
        String missingId = "missingId";
        @NonFinal
        @Member(idProperty = "customId")
        Child child;
    }


    void expectEntity(Class<?> parentClass, Predicate<Entity<?, ?>> predicate) {
        expectEntities(parentClass, entities -> entities.stream().anyMatch(predicate));
    }

    void expectNoEntity(Class<?> parentClass, Predicate<Entity<?, ?>> predicate) {
        expectEntities(parentClass, entities -> entities.stream().noneMatch(predicate));
    }

    void expectEntities(Class<?> parentClass, Predicate<Collection<Entity<?, ?>>> predicate) {
        TestFixture.create()
                .given(fc -> loadAggregate("test", (Class) parentClass, false)
                        .update(s -> safelyCall(() -> parentClass.getConstructor().newInstance())))
                .whenApplying(fc -> loadAggregate("test", (Class) parentClass).entities())
                .expectResult(predicate);
    }

    @Value
    static class CommandWithRoutingKey {
        @RoutingKey
        String target;

        @AssertLegal
        void assertLegal(Child child) {
            throw new IllegalCommandException("Child is unauthorized");
        }
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
