package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.IllegalCommandException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.Value;
import lombok.With;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;

@SuppressWarnings({"rawtypes", "SameParameterValue", "unchecked"})
public class AggregateEntitiesTest {
    private final TestFixture testFixture = (TestFixture) TestFixture.create().given(
            fc -> loadAggregate("test", Aggregate.class, false).update(s -> Aggregate.builder().build()));

    void expectEntity(Predicate<Entity<?, ?>> predicate) {
        expectEntities(Aggregate.class, entities -> entities.stream().anyMatch(predicate));
    }

    void expectNoEntity(Predicate<Entity<?, ?>> predicate) {
        expectEntities(Aggregate.class, entities -> entities.stream().noneMatch(predicate));
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
            expectEntity(e -> "id".equals(e.id()) && "childId".equals(e.idProperty()));
        }

        @Test
        void findSingletonWithCustomPath() {
            expectEntity(e -> "otherId".equals(e.id()) && "customId".equals(e.idProperty()));
        }

        @Test
        void noEntityIfNull() {
            expectNoEntity(e -> "missingId".equals(e.id()));
        }

        @Test
        void findEntitiesInList() {
            expectEntity(e -> "list0".equals(e.id()));
            expectEntity(e -> "list1".equals(e.id()));
            expectEntity(e -> e.id() == null);
        }

        @Test
        void findEntitiesInMapUsingKey() {
            expectEntity(e -> new Key("map0").equals(e.id()));
            expectEntity(e -> new Key("map1").equals(e.id()));
        }

        @Test
        void findGrandChild() {
            expectEntity(
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

        @Nested
        class SingletonTests {
            @Test
            void testAddSingleton() {
                testFixture.whenCommand(new AddChild("missing"))
                        .expectThat(fc -> expectEntity(
                                e -> e.get() instanceof MissingChild && "missing".equals(e.id())));
            }

            @Test
            void testAddChildAndGrandChild() {
                testFixture.whenCommand(new AddChildAndGrandChild("missing", "missingGc"))
                        .expectThat(fc -> {
                            expectEntity(e -> Objects.equals(e.id(), "missing"));
                            expectEntity(e -> Objects.equals(e.id(), "missingGc"));
                        });
            }

            @Test
            void testUpdateSingleton() {
                testFixture.whenCommand(new UpdateChild("id", "data"))
                        .expectThat(fc -> expectEntity(
                                e -> e.get() instanceof Child && ((Child) e.get()).getData().equals("data")));
            }

            @Test
            void testRemoveSingleton() {
                testFixture.whenCommand(new RemoveChild("id"))
                        .expectThat(fc -> {
                            expectNoEntity(e -> "id".equals(e.id()));
                            expectEntity(e -> "otherId".equals(e.id()));
                        });
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
        }

        @Nested
        class ListTests {
            @Test
            void testAddListChild() {
                testFixture.whenCommand(new AddListChild("list2"))
                        .expectThat(fc -> expectEntity(
                                e -> e.get() instanceof ListChild && "list2".equals(e.id())))
                        .expectTrue(fc -> loadAggregate("test", Aggregate.class).get().getList().size() == 4);
            }

            @Test
            void testUpdateListChild() {
                testFixture.whenCommand(new UpdateChild("list1", "data"))
                        .expectTrue(fc -> loadAggregate("test", Aggregate.class).get().getList().get(1).getData().equals("data"));
            }

            @Test
            void testRemoveListChild() {
                testFixture.whenCommand(new RemoveChild("list1"))
                        .expectThat(fc -> expectNoEntity(e -> "list1".equals(e.id())))
                        .expectTrue(fc -> loadAggregate("test", Aggregate.class).get().getList().size() == 2);
            }

            @Value
            class AddListChild {
                String listChildId;

                @Apply
                ListChild apply() {
                    return ListChild.builder().listChildId(listChildId).build();
                }
            }
        }

        @Nested
        class MapTests {
            @Test
            void testAddMapChild() {
                testFixture.whenCommand(new AddMapChild(new Key("map2")))
                        .expectThat(fc -> expectEntity(
                                e -> e.get() instanceof MapChild && new Key("map2").equals(e.id())))
                        .expectTrue(fc -> loadAggregate("test", Aggregate.class).get().getMap().size() == 3);
            }

            @Test
            void testUpdateMapChild() {
                testFixture.whenCommand(new UpdateChild(new Key("map1"), "data"))
                        .expectTrue(fc -> loadAggregate("test", Aggregate.class).get().getMap().get(new Key("map1")).getData().equals("data"));
            }

            @Test
            void testRemoveMapChild() {
                testFixture.whenCommand(new RemoveChild(new Key("map1")))
                        .expectThat(fc -> expectNoEntity(e -> new Key("map1").equals(e.id())))
                        .expectTrue(fc -> loadAggregate("test", Aggregate.class).get().getMap().size() == 1);
            }

            @Value
            class AddMapChild {
                Key mapChildId;

                @Apply
                MapChild apply() {
                    return MapChild.builder().mapChildId(mapChildId).build();
                }
            }
        }

        @Value
        class RemoveChild {
            @RoutingKey
            Object id;

            @Apply
            Object apply(Updatable target) {
                return null;
            }
        }

        @Value
        class UpdateChild {
            @RoutingKey
            Object childId;
            Object data;

            @Apply
            Object apply(Updatable child) {
                return child.withData(data);
            }
        }

        class CommandHandler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate("test", Aggregate.class).apply(command);
            }
        }
    }

    @Nested
    class MutableEntityTests {
        private final TestFixture testFixture = (TestFixture) TestFixture.create(new CommandHandler()).given(
                fc -> loadAggregate("test", MutableAggregate.class, false)
                        .update(s -> new MutableAggregate(null)));

        @Test
        void createMutableEntity() {
            testFixture.whenCommand(new CreateMutableEntity("childId")).expectThat(
                    fc -> expectEntity(MutableAggregate.class, e -> "childId".equals(e.id())));
        }

        @Test
        void deleteMutableEntity() {
            testFixture.givenCommands(new CreateMutableEntity("childId"))
                    .whenCommand(new DeleteMutableEntity("childId")).expectThat(
                    fc -> expectNoEntity(MutableAggregate.class, e -> "childId".equals(e.id())));
        }

        class CommandHandler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate("test", MutableAggregate.class).apply(command);
            }
        }

        @Value
        class CreateMutableEntity {
            @RoutingKey
            String id;

            @Apply
            MutableEntity create() {
                return new MutableEntity(id);
            }
        }

        @Value
        class DeleteMutableEntity {
            @RoutingKey
            String id;

            @Apply
            MutableEntity delete(MutableEntity entity) {
                return null;
            }
        }

        @Data
        @AllArgsConstructor
        class MutableAggregate {
            @Member
            MutableEntity child;
        }

        @Data
        @AllArgsConstructor
        class MutableEntity {
            @EntityId
            String id;
        }

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

    }


    @Value
    @Builder
    public static class Aggregate {

        @EntityId
        @Default
        String id = UUID.randomUUID().toString();

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
        @With
        List<ListChild> list = List.of(
                ListChild.builder().listChildId("list0").build(),
                ListChild.builder().listChildId("list1").build(), ListChild.builder().listChildId(null).build());

        @Member
        @Default
        @With
        Map<Key, MapChild> map = Map.of(
                new Key("map0"), MapChild.builder().mapChildId(new Key("map0")).build(),
                new Key("map1"), MapChild.builder().build());

        @Member
        @Default
        @With
        ChildWithChild childWithGrandChild = ChildWithChild.builder().build();
    }

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    static class Child implements Updatable {
        @EntityId
        @Default
        String childId = "id";
        @Default
        String customId = "otherId";
        @Member
        GrandChild grandChild;
        @With
        Object data;
    }

    @Value
    @Builder(toBuilder = true)
    static class ListChild implements Updatable {
        @EntityId
        String listChildId;
        @With
        Object data;
    }

    @Value
    @Builder
    static class MapChild implements Updatable {
        @EntityId
        Key mapChildId;
        @With
        Object data;
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


    @Value
    static class Key {
        String key;

        @Override
        public String toString() {
            return key;
        }
    }

    interface Updatable {
        Object withData(Object data);
    }
}
