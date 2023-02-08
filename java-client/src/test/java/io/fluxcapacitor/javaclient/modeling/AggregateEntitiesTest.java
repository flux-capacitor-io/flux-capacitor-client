package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.Nullable;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.InterceptApply;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import io.fluxcapacitor.javaclient.tracking.handling.IllegalCommandException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;
import static io.fluxcapacitor.javaclient.FluxCapacitor.loadEntity;
import static java.util.stream.Collectors.toList;

@Slf4j
@SuppressWarnings({"rawtypes", "SameParameterValue", "unchecked"})
public class AggregateEntitiesTest {
    private TestFixture testFixture;

    @BeforeEach
    void setUp() {
        testFixture = TestFixture.create().given(
                fc -> loadAggregate("test", Aggregate.class).update(s -> Aggregate.builder().build()));
    }

    void expectEntity(Predicate<Entity<?>> predicate) {
        expectEntities(Aggregate.class, entities -> entities.stream().anyMatch(predicate));
    }

    void expectNoEntity(Predicate<Entity<?>> predicate) {
        expectEntities(Aggregate.class, entities -> entities.stream().noneMatch(predicate));
    }

    void expectEntities(Class<?> parentClass, Predicate<Collection<Entity<?>>> predicate) {
        testFixture
                .whenApplying(fc -> loadAggregate("test", (Class<?>) parentClass).allEntities().collect(toList()))
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
            expectEntity(e -> e.entities().stream().findFirst().map(c -> "grandChild".equals(c.id())).orElse(false));
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
                    .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
        }

        @Test
        void testRouteToGrandchild() {
            testFixture.whenCommand(new CommandWithRoutingKey("grandChild"))
                    .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
        }

        @Test
        void testNoChildRoute() {
            testFixture.whenCommand(new CommandWithoutRoutingKey("somethingRandom")).expectSuccessfulResult();
        }

        @Test
        void testPropertyMatchesChild() {
            testFixture.whenCommand(new CommandWithoutRoutingKey("otherId"))
                    .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
        }

        @Test
        void testPropertyValueMatchesNothing() {
            testFixture.whenCommand(new CommandWithoutRoutingKey("somethingRandom")).expectSuccessfulResult();
        }

        @Test
        void testPropertyPathMatchesNothing() {
            testFixture.whenCommand(new CommandWithWrongProperty("id")).expectSuccessfulResult()
                    .expectEvents(new CommandWithWrongProperty("id"));
        }

        @Test
        void testRouteToGrandchildButFailingOnChild() {
            testFixture.whenCommand(new CommandTargetingGrandchildButFailingOnParent("grandChild"))
                    .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
        }

        @Test
        void updateCommandExpectsExistingChild() {
            testFixture.whenCommand(new UpdateCommandThatFailsIfChildDoesNotExist("whatever"))
                    .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
        }

        @Test
        void updateCommandExpectsExistingChild_kotlin() {
            testFixture.whenCommand(new KotlinUpdateCommandThatFailsIfChildDoesNotExist("whatever"))
                    .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
        }

        @Test
        void testListChildAssertion() {
            testFixture.whenCommand(new CommandWithRoutingKey("list0"))
                    .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
        }

        @Test
        void assertLegalOnChildEntity() {
            AggregateEntitiesTest.this.setUp();
            testFixture.registerHandlers(new Object() {
                @HandleCommand
                void handle(CommandWithRoutingKey command) {
                    loadEntity(command.getTarget()).assertLegal(command);
                }
            });
            testFixture.whenCommand(new CommandWithRoutingKey("list0"))
                    .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
        }

        @Test
        void testRouteToChildHandledByEntity() {
            testFixture.whenCommand(new CommandWithRoutingKeyHandledByEntity("id"))
                    .expectExceptionalResult(IllegalCommandException.class).expectNoEvents();
        }

        class CommandHandler {
            @HandleCommand
            void handle(Object command) {
                loadAggregate("test", Aggregate.class).assertAndApply(command);
            }
        }
    }

    @Nested
    class InterceptApplyTests {
        @BeforeEach
        void setUp() {
            testFixture.registerHandlers(new Object() {
                @HandleCommand
                void handle(Object command) {
                    loadAggregate("test", Aggregate.class).assertAndApply(command);
                }
            });
        }

        @Test
        void commandWithoutInterceptTriggersException() {
            testFixture.whenCommand(new FailingCommand()).expectExceptionalResult(MockException.class);
        }

        @Test
        void ignoreApplyByReturningVoid() {
            testFixture.whenCommand(new FailingCommand() {
                @InterceptApply
                void intercept() {
                }
            }).expectNoResult();
        }

        @Test
        void ignoreApplyByReturningNull() {
            testFixture.whenCommand(new FailingCommand() {
                @InterceptApply
                Object intercept() {
                    return null;
                }
            }).expectNoResult();
        }

        @Test
        void ignoreApplyByReturningEmptyStream() {
            testFixture.whenCommand(new FailingCommand() {
                @InterceptApply
                Stream<?> intercept() {
                    return Stream.empty();
                }
            }).expectNoResult();
        }

        @Test
        void ignoreApplyByReturningEmptyCollection() {
            testFixture.whenCommand(new FailingCommand() {
                @InterceptApply
                Collection<?> intercept() {
                    return List.of();
                }
            }).expectNoResult().expectNoEvents();
        }

        @Test
        void returnDifferentCommand() {
            testFixture.whenEventsAreApplied("test", Aggregate.class, new Message(new FailingCommand() {
                @InterceptApply
                Object intercept(FailingCommand input) {
                    return new AddChild("missing");
                }
            }, Metadata.of("foo", "bar")))
                    .expectEvents((Predicate<Message>) m -> m.getMetadata().containsKey("foo"))
                    .expectThat(fc -> expectEntity(e -> e.get() instanceof MissingChild && "missing".equals(e.id())));
        }

        @Test
        void returnTwoCommands() {
            testFixture.whenCommand(new FailingCommand() {
                @InterceptApply
                List<?> intercept() {
                    return List.of(new AddChild("missing"), new UpdateChild("id", "data"));
                }
            }).expectThat(fc -> expectEntity(
                    e -> e.get() instanceof Child && ((Child) e.get()).getData().equals("data")));
        }

        @Test
        void returnTwoCommandsSecondFails() {
            testFixture.whenCommand(new FailingCommand() {
                @InterceptApply
                List<?> intercept() {
                    return List.of(new AddChild("missing"), new FailingCommand());
                }
            }).expectExceptionalResult(MockException.class);
        }

        @Test
        void returnNestedCommands() {
            testFixture.whenCommand(new Object() {
                @InterceptApply
                Object intercept() {
                    return new UpdateChildNested();
                }
            }).expectThat(fc -> expectEntity(
                    e -> e.get() instanceof Child && ((Child) e.get()).getData().equals("data")));
        }

        class FailingCommand {
            @Getter
            private final String missingChildId = "123";

            @AssertLegal
            void apply(Aggregate aggregate) {
                throw new MockException();
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
        class UpdateChild {
            @RoutingKey
            Object childId;
            Object data;

            @Apply
            Object apply(Updatable child) {
                return child.withData(data);
            }
        }

        @Value
        class UpdateChildNested {
            @InterceptApply
            List<?> intercept() {
                return List.of(new AddChild("missing"), new UpdateChild("id", "data"));
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
                            expectEntity(e -> e.root().previous().allEntities().anyMatch(p -> "id".equals(p.id())));
                            expectEntity(e -> "otherId".equals(e.id()));
                            expectEntity(e -> e.previous() != null && "otherId".equals(e.previous().id()));
                        });
            }

            @Test
            void applyOnChildEntity() {
                AggregateEntitiesTest.this.setUp();
                testFixture.registerHandlers(new Object() {
                    @HandleCommand
                    void handle(UpdateChild command) {
                        loadEntity(command.getChildId()).assertAndApply(command);
                    }
                });
                testFixture.whenCommand(new UpdateChild("id", "data"))
                        .expectThat(fc -> expectEntity(
                                e -> e.get() instanceof Child && ((Child) e.get()).getData().equals("data")));
            }

            @Test
            void addStringMember() {
                testFixture.whenCommand(new Object() {
                            @Apply
                            Aggregate apply(Aggregate aggregate) {
                                return aggregate.toBuilder().clientReference("clientRef").build();
                            }
                        })
                        .expectThat(fc -> expectEntity(e -> "clientRef".equals(e.id()) && "clientRef".equals(e.get())))
                        .expectTrue(fc -> "clientRef".equals(FluxCapacitor.loadEntity("clientRef").get()))
                        .expectTrue(fc -> FluxCapacitor.loadEntityValue("clientRef").filter("clientRef"::equals)
                                .isPresent());
            }

            @Test
            void checkIfEventHandlerGetsEntity() {
                testFixture.registerHandlers(new EventHandler())
                        .whenCommand(new AddChild("missing"))
                        .expectEvents("added child to: test");
            }

            @Test
            void checkIfEventHandlerGetsEntity_unwrapped() {
                testFixture.registerHandlers(new EventHandler())
                        .whenCommand(new UpdateChild("id", "missing"))
                        .expectEvents("updated child of: test");
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
                    return MissingChild.builder().missingChildId(missingChildId)
                            .grandChild(new MissingGrandChild(missingGrandChildId)).build();
                }
            }

            class EventHandler {
                @HandleEvent
                void handle(AddChild event, Entity<Aggregate> entity) {
                    FluxCapacitor.publishEvent("added child to: " + entity.id());
                }

                @HandleEvent
                void handle(UpdateChild event, Aggregate entity) {
                    FluxCapacitor.publishEvent("updated child of: " + entity.getId());
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
                        .expectTrue(fc -> loadAggregate("test", Aggregate.class).get().getList().get(1).getData()
                                .equals("data"));
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
                        .expectTrue(fc -> loadAggregate("test", Aggregate.class).get().getMap().get(new Key("map1"))
                                .getData().equals("data"));
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
                MapChild apply(@NonNull Aggregate aggregate, @NonNull Metadata metadata) {
                    return MapChild.builder().mapChildId(mapChildId).build();
                }
            }
        }

        @Value
        class RemoveChild {
            @RoutingKey
            Object id;

            @Apply
            Object apply(Updatable target, @NonNull Aggregate aggregate, @NonNull Metadata metadata) {
                return null;
            }
        }

        @Value
        class UpdateChild {
            @RoutingKey
            Object childId;
            Object data;

            @Apply
            Object apply(Updatable child, @NonNull Aggregate aggregate, @NonNull Metadata metadata) {
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
                fc -> loadAggregate("test", MutableAggregate.class)
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

        void expectEntity(Class<?> parentClass, Predicate<Entity<?>> predicate) {
            expectEntities(parentClass, entities -> entities.stream().anyMatch(predicate));
        }

        void expectNoEntity(Class<?> parentClass, Predicate<Entity<?>> predicate) {
            expectEntities(parentClass, entities -> entities.stream().noneMatch(predicate));
        }

        void expectEntities(Class<?> parentClass, Predicate<Collection<Entity<?>>> predicate) {
            testFixture
                    .whenApplying(fc -> loadAggregate("test", (Class) parentClass).allEntities().collect(toList()))
                    .expectResult(predicate);
        }

    }

    @Nested
    class loadForTests {
        @Test
        void loadAggregateForEntity() {
            testFixture.whenApplying(fc -> FluxCapacitor.loadAggregateFor("map0"))
                    .<Entity<Aggregate>>expectResult(a -> a.get() != null);
        }

        @Test
        void loadForNewEntityReturnsDefault() {
            testFixture.whenApplying(fc -> FluxCapacitor.loadAggregateFor("unknown", Aggregate.class))
                    .<Entity<Aggregate>>expectResult(a -> a.get() == null);
        }

        @Test
        void loadForNewEntityReturnsDefaultClass() {
            testFixture.whenApplying(fc -> FluxCapacitor.loadAggregateFor("unknown", MapChild.class))
                    .<Entity<MapChild>>expectResult(a -> a.get() == null && a.type().equals(MapChild.class));
        }
    }

    @Value
    @Builder(toBuilder = true)
    public static class Aggregate {

        @Default
        String id = "test";

        @Member
        @Default
        Child singleton = Child.builder().build();

        @Member(idProperty = "customId")
        @Default
        Child singletonCustomPath = Child.builder().build();

        @Member
        MissingChild missingChild;

        @Member
        @Default
        List<ListChild> list = List.of(
                ListChild.builder().listChildId("list0").build(),
                ListChild.builder().listChildId("list1").build(), ListChild.builder().listChildId(null).build());

        @Member
        @Default
        Map<Key, MapChild> map = Map.of(
                new Key("map0"), MapChild.builder().mapChildId(new Key("map0")).build(),
                new Key("map1"), MapChild.builder().build());

        @Member
        @Default
        @With
        ChildWithChild childWithGrandChild = ChildWithChild.builder().build();

        @Member
        String clientReference;
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

        @AssertLegal
        void assertLegal(CommandWithRoutingKeyHandledByEntity child) {
            throw new IllegalCommandException("Child already exists");
        }
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
    public static class MissingChild {
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
    static class CommandWithRoutingKeyHandledByEntity {
        @RoutingKey
        String target;
    }

    @Value
    static class CommandWithoutRoutingKey {
        String customId;

        @AssertLegal
        void assertLegal(Child child) {
            throw new IllegalCommandException("Child should not have been targeted");
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
        void assertLegal(@Nullable MissingChild child, @NonNull Aggregate aggregate) {
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
