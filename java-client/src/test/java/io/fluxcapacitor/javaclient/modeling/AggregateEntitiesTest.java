package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.IllegalCommandException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.With;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;

@SuppressWarnings({"rawtypes", "SameParameterValue", "unchecked"})
public class AggregateEntitiesTest {
    private final TestFixture testFixture = (TestFixture) TestFixture.create().given(
            fc -> loadAggregate("test", Aggregate.class, false).update(s -> Aggregate.builder().build()));

    void expectEntity(Class<?> parentClass, Predicate<Entity<?, ?>> predicate) {
        expectEntities(parentClass, entities -> entities.stream().anyMatch(predicate));
    }

    void expectNoEntity(Class<?> parentClass, Predicate<Entity<?, ?>> predicate) {
        expectEntities(parentClass, entities -> entities.stream().noneMatch(predicate));
    }

    void expectEntities(Class<?> parentClass, Predicate<Collection<Entity<?, ?>>> predicate) {
        testFixture
                .whenApplying(fc -> loadAggregate("test", (Class) parentClass).allEntities())
                .expectResult(predicate);
    }

    @Nested
    class FindEntityTests {

        @Test
        void findSingleton() {
            expectEntity(Aggregate.class, e -> "id".equals(e.id()) && "childId".equals(e.idProperty()));
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
            expectEntity(Aggregate.class,
                         e -> e.entities().stream().findFirst().map(c -> "grandChild".equals(c.id())).orElse(false));
        }
    }

    @Nested
    class AssertLegalTests {
        @BeforeEach
        void setUp() {
            testFixture.registerHandlers(new CommandHandler());
        }

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
            testFixture.whenCommand(new CommandWithoutRoutingKey("somethingRandom")).expectNoException();
        }

        @Test
        void testPropertyMatchesChild() {
            testFixture.whenCommand(new CommandWithoutRoutingKey("otherId"))
                    .expectException(IllegalCommandException.class);
        }

        @Test
        void testPropertyValueMatchesNothing() {
            testFixture.whenCommand(new CommandWithoutRoutingKey("somethingRandom")).expectNoException();
        }

        @Test
        void testPropertyPathMatchesNothing() {
            testFixture.whenCommand(new CommandWithWrongProperty("id")).expectNoException();
        }

        @Test
        void testRouteToGrandchildButFailingOnChild() {
            testFixture.whenCommand(new CommandTargetingGrandchildButFailingOnParent("grandChild"))
                    .expectException(IllegalCommandException.class);
        }

        @Test
        void updateCommandExpectsExistingChild() {
            testFixture.whenCommand(new UpdateCommandThatFailsIfChildDoesNotExist("whatever"))
                    .expectException(IllegalCommandException.class);
        }

        @Test
        void testListChildAssertion() {
            testFixture.whenCommand(new CommandWithRoutingKey("list0"))
                    .expectException(IllegalCommandException.class);
        }

        class CommandHandler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate("test", Aggregate.class).assertLegal(command);
            }
        }
    }

    @Nested
    class ApplyTests {

        @BeforeEach
        void setUp() {
            testFixture.registerHandlers(new CommandHandler());
        }

        @Test
        void testAddSingleton() {
            testFixture.whenCommand(new AddChild("missing"))
                    .expectThat(fc -> expectEntity(
                            Aggregate.class, e -> e.get() instanceof MissingChild && "missing".equals(e.id())));
        }

        @Test
        void testAddChildAndGrandChild() {
            testFixture.whenCommand(new AddChildAndGrandChild("missing", "missingGc"))
                    .expectThat(fc -> {
                        expectEntity(Aggregate.class, e -> Objects.equals(e.id(), "missing"));
                        expectEntity(Aggregate.class, e -> Objects.equals(e.id(), "missingGc"));
                    });
        }

        @Test
        void testUpdateSingleton() {
            testFixture.whenCommand(new UpdateChild("id"))
                    .expectThat(fc -> expectEntity(
                            Aggregate.class, e -> e.get() instanceof Child && ((Child) e.get()).getCustomId().equals("updatedCustomId")));
        }

        @Test
        void testRemoveSingleton() {
            testFixture.whenCommand(new RemoveChild("id"))
                    .expectThat(fc -> {
                        expectNoEntity(Aggregate.class, e -> "id".equals(e.id()));
                        expectEntity(Aggregate.class, e -> "otherId".equals(e.id()));
                    });
        }

        @Value
        class RemoveChild {
            @RoutingKey
            String id;

            @Apply
            Child apply(Child target) {
                return null;
            }
        }

        @Value
        class AddChild {
            String missingChildId;

            @Apply
            MissingChild apply() {
                return MissingChild.builder().missingChildId(missingChildId).build();
            }
        }

        @Value
        class AddChildAndGrandChild {
            String missingChildId;
            String missingGrandChildId;

            @Apply
            MissingChild createChild() {
                return MissingChild.builder().missingChildId(missingChildId).build();
            }

            @Apply
            MissingGrandChild createGrandChild() {
                return new MissingGrandChild(missingGrandChildId);
            }
        }

        @Value
        class UpdateChild {
            String childId;

            @Apply
            Child apply(Child child) {
                return child.toBuilder().customId("updatedCustomId").build();
            }
        }

        class CommandHandler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate("test", Aggregate.class).apply(command);
            }
        }
    }


    @Value
    @Builder
    public static class Aggregate {
        @Member
        @Default
        @With
        Child singleton = Child.builder().build();

        @Member(idProperty = "customId")
        @Default
        Child singletonCustomPath = Child.builder().build();

        @Member
        @With
        MissingChild missingChild;

        @Member
        @Default
        List<ListChild> list = List.of(
                ListChild.builder().listChildId("list0").build(),
                ListChild.builder().listChildId("list1").build(), ListChild.builder().listChildId(null).build());

        @Member
        @Default
        Map<?, MapChild> map = Map.of(
                "map0", MapChild.builder().build(), new Key("map1"), MapChild.builder().build());

        @Member
        @Default
        ChildWithChild childWithGrandChild = ChildWithChild.builder().build();
    }

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    static class Child {
        @EntityId
        @Default
        String childId = "id";

        @Default
        String customId = "otherId";

        @Member
        GrandChild grandChild;
    }

    @Value
    @Builder
    static class ListChild {
        @EntityId
        String listChildId;
    }

    @Value
    @Builder
    static class MapChild {
        @EntityId
        String mapChildId;
    }

    @Value
    @Builder
    static class MissingChild {
        @EntityId
        String missingChildId;

        @Member
        @With
        MissingGrandChild grandChild;
    }

    @Value
    @Builder
    static class MissingGrandChild {
        @EntityId
        String missingGrandChildId;
    }

    @Value
    @AllArgsConstructor
    @Builder
    static class ChildWithChild {
        @EntityId
        @Default
        String withChildId = "withChild";

        @Member
        GrandChild grandChild = new GrandChild("grandChild");
    }

    @Value
    static class GrandChild {
        @EntityId
        String grandChildId;
    }

    @Value
    static class CommandWithRoutingKey {
        @RoutingKey
        String target;

        @AssertLegal
        void assertLegal(Object child) {
            if (child != null) {
                throw new IllegalCommandException("Child is unauthorized");
            }
        }
    }

    @Value
    static class CommandWithoutRoutingKey {
        String customId;

        @AssertLegal
        void assertLegal(Object child) {
            if (child != null && !(child instanceof Aggregate)) {
                throw new IllegalCommandException("Child is unauthorized");
            }
        }
    }

    @Value
    static class CommandTargetingGrandchildButFailingOnParent {
        @RoutingKey
        String id;

        @AssertLegal
        void assertLegal(ChildWithChild child) {
            if (child != null) {
                throw new IllegalCommandException("Child is unauthorized");
            }
        }
    }

    @Value
    static class CommandWithWrongProperty {
        String randomProperty;

        @AssertLegal
        void assertLegal(Child child) {
            if (child != null) {
                throw new IllegalCommandException("Child is unauthorized");
            }
        }
    }

    @Value
    static class UpdateCommandThatFailsIfChildDoesNotExist {
        String missingChildId;

        @AssertLegal
        void assertLegal(MissingChild child) {
            if (child == null) {
                throw new IllegalCommandException("Expected a child");
            }
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
