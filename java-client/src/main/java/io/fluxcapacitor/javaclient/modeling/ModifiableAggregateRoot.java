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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.Invocation;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.javaclient.modeling.EventPublication.IF_MODIFIED;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

/**
 * A mutable, stateful {@link AggregateRoot} implementation that allows in-place updates and applies events
 * with commit support for persisting the state and synchronizing it with the Flux platform.
 * <p>
 * This class acts as a wrapper around an immutable entity (typically an {@link ImmutableAggregateRoot}),
 * providing additional lifecycle management for:
 * <ul>
 *     <li>Capturing uncommitted changes during an event or command handler execution.</li>
 *     <li>Intercepting and applying updates and events with legality assertions.</li>
 *     <li>Buffering applied events and committing them via a {@link CommitHandler} callback.</li>
 *     <li>Supporting batch-commit semantics for grouped event processing.</li>
 *     <li>Tracking active aggregates for correlation and relationship resolution during handlers.</li>
 * </ul>
 *
 * <h2>Commit Lifecycle</h2>
 * During handler invocation, intercepted updates and applied events are stored in a temporary buffer.
 * When the handler completes successfully, events are committed via the configured {@link CommitHandler},
 * which typically stores events in the {@link EventStore} and
 * publishes them if needed.
 * <p>
 * If the handler fails (throws), state changes and captured events are discarded, and the aggregate is rolled
 * back to the last stable state.
 *
 * <h2>Publication Strategy</h2>
 * Events are published according to the {@link EventPublication} and {@link EventPublicationStrategy} specified
 * either globally or on individual {@link Apply}-annotated methods. This allows
 * fine-grained control over which events are stored or published.
 *
 * <h2>Thread-Scoped Aggregates</h2>
 * All active {@code ModifiableAggregateRoot} instances are tracked per thread using a thread-local map,
 * enabling relationship resolution and update tracking across aggregates within the same handler context.
 * <p>
 * This mechanism enables other parts of the framework to determine which aggregates are currently in use
 * or updated within a processing thread, without requiring external state.
 *
 * @param <T> the type of the aggregate's value.
 *
 * @see ImmutableAggregateRoot
 * @see LazyAggregateRoot
 * @see CommitHandler
 * @see EventPublication
 * @see EventPublicationStrategy
 */
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ModifiableAggregateRoot<T> extends DelegatingEntity<T> implements AggregateRoot<T> {

    private static final ThreadLocal<Map<String, ModifiableAggregateRoot<?>>> activeAggregates =
            ThreadLocal.withInitial(LinkedHashMap::new);

    @SuppressWarnings("unchecked")
    public static <T> Optional<ModifiableAggregateRoot<T>> getIfActive(Object aggregateId) {
        return ofNullable((ModifiableAggregateRoot<T>) activeAggregates.get().get(aggregateId.toString()));
    }

    public static Map<String, Class<?>> getActiveAggregatesFor(@NonNull Object entityId) {
        List<Entity<?>> candidates = activeAggregates.get().values().stream()
                .filter(a -> a.getEntity(entityId).isPresent()).collect(Collectors.toList());
        Comparator<Entity<?>> byPresent = Comparator.comparing(
                a -> a.getEntity(entityId).map(Entity::isPresent).orElse(false));
        Comparator<Entity<?>> byOrder = Comparator.comparing(candidates::indexOf);
        return candidates.stream()
                .sorted(byPresent.thenComparing(byOrder))
                .collect(toMap(e -> e.id().toString(), Entity::type, (a, b) -> b, LinkedHashMap::new));
    }

    public static <T> Entity<T> load(
            Object aggregateId, Supplier<Entity<T>> loader, boolean commitInBatch, EventPublication eventPublication,
            EventPublicationStrategy publicationStrategy,
            EntityHelper entityHelper, Serializer serializer, DispatchInterceptor dispatchInterceptor,
            CommitHandler commitHandler) {
        return ModifiableAggregateRoot.<T>getIfActive(aggregateId).orElseGet(
                () -> new ModifiableAggregateRoot<>(loader.get(), commitInBatch, eventPublication, publicationStrategy,
                                                    entityHelper, serializer, dispatchInterceptor, commitHandler));
    }

    private Entity<T> lastCommitted;
    private Entity<T> lastStable;
    private final boolean commitInBatch;

    private final EventPublication aggregateEventPublication;
    private final EventPublicationStrategy aggregatePublicationStrategy;
    private final EntityHelper entityHelper;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final CommitHandler commitHandler;

    private final AtomicBoolean waitingForHandlerEnd = new AtomicBoolean(), waitingForBatchEnd = new AtomicBoolean();
    private final List<AppliedEvent> applied = new ArrayList<>(), uncommitted = new ArrayList<>();
    private final List<UnaryOperator<Entity<T>>> queued = new ArrayList<>();
    private volatile boolean updating, committing, commitPending;

    protected ModifiableAggregateRoot(Entity<T> delegate, boolean commitInBatch,
                                      EventPublication eventPublication, EventPublicationStrategy publicationStrategy,
                                      EntityHelper entityHelper, Serializer serializer,
                                      DispatchInterceptor dispatchInterceptor, CommitHandler commitHandler) {
        super(delegate);
        this.entityHelper = entityHelper;
        this.lastCommitted = delegate;
        this.lastStable = delegate;
        this.commitInBatch = commitInBatch;
        this.aggregateEventPublication = eventPublication;
        this.aggregatePublicationStrategy = publicationStrategy;
        this.serializer = serializer;
        this.dispatchInterceptor = dispatchInterceptor;
        this.commitHandler = commitHandler;
    }

    @Override
    public <E extends Exception> Entity<T> assertLegal(Object update) throws E {
        entityHelper.intercept(update, this).forEach(c -> entityHelper.assertLegal(c, this));
        return this;
    }

    @Override
    public Entity<T> assertAndApply(Object payloadOrMessage) {
        entityHelper.intercept(payloadOrMessage, this).forEach(m -> apply(Message.asMessage(m), true));
        return this;
    }

    @Override
    public Entity<T> update(UnaryOperator<T> function) {
        return handleUpdate(a -> a.update(function));
    }

    @Override
    public Entity<T> apply(Message message) {
        entityHelper.intercept(message, this).forEach(m -> apply(Message.asMessage(m), false));
        return this;
    }

    protected Entity<T> apply(Message message, boolean assertLegal) {
        return handleUpdate(a -> {
            if (assertLegal) {
                entityHelper.assertLegal(message, a);
            }

            Optional<Apply> applyAnnotation = entityHelper.applyInvoker(
                            new DeserializingMessage(message, EVENT, null, serializer), a, true)
                    .map(HandlerInvoker::getMethodAnnotation);

            var eventPublication = applyAnnotation.map(Apply::eventPublication)
                    .filter(ep -> ep != EventPublication.DEFAULT).orElse(this.aggregateEventPublication);

            int hashCodeBefore = eventPublication == IF_MODIFIED ? a.get() == null ? -1 : a.get().hashCode() : -1;

            Entity<T> result = a.apply(message);
            if (switch (eventPublication) {
                case ALWAYS, DEFAULT -> true;
                case IF_MODIFIED -> !Objects.equals(a.get(), result.get())
                                    || (result.get() != null && result.get().hashCode() != hashCodeBefore);
                case NEVER -> false;
            }) {
                Message intercepted = dispatchInterceptor.interceptDispatch(message, EVENT, null);
                if (intercepted == null) {
                    return a;
                }
                var publicationStrategy = applyAnnotation.map(Apply::publicationStrategy)
                        .filter(ep -> ep != EventPublicationStrategy.DEFAULT).orElse(this.aggregatePublicationStrategy);
                Message m = publicationStrategy == EventPublicationStrategy.PUBLISH_ONLY
                        ? intercepted.addMetadata(Entity.AGGREGATE_ID_METADATA_KEY, id().toString(),
                                                  Entity.AGGREGATE_TYPE_METADATA_KEY, type().getName())
                        : intercepted.addMetadata(Entity.AGGREGATE_ID_METADATA_KEY, id().toString(),
                                                  Entity.AGGREGATE_TYPE_METADATA_KEY, type().getName(),
                                                  Entity.AGGREGATE_SN_METADATA_KEY,
                                                  String.valueOf(getDelegate().sequenceNumber() + 1L));
                var serializedEvent =
                        dispatchInterceptor.modifySerializedMessage(m.serialize(serializer), m, EVENT, null);
                if (serializedEvent == null) {
                    return a;
                }
                applied.add(new AppliedEvent(new DeserializingMessage(serializedEvent, type ->
                        serializer.convert(m.getPayload(), type), EVENT, null, serializer), publicationStrategy));
            }
            return result;
        });
    }

    protected Entity<T> handleUpdate(UnaryOperator<Entity<T>> update) {
        if (updating) {
            queued.add(update);
            return this;
        }
        try {
            updating = true;
            boolean firstUpdate = waitingForHandlerEnd.compareAndSet(false, true);
            if (firstUpdate) {
                activeAggregates.get().putIfAbsent(id().toString(), this);
            }
            try {
                delegate = update.apply(getDelegate());
            } finally {
                if (firstUpdate) {
                    Invocation.whenHandlerCompletes((r, e) -> whenHandlerCompletes(e));
                }
            }
        } finally {
            updating = false;
        }
        while (!queued.isEmpty()) {
            delegate = queued.removeFirst().apply(getDelegate());
        }
        return this;
    }

    protected void whenHandlerCompletes(Throwable error) {
        waitingForHandlerEnd.set(false);
        if (error == null) {
            uncommitted.addAll(applied);
            lastStable = delegate;
        } else {
            delegate = lastStable;
        }
        applied.clear();
        if (!commitInBatch) {
            commit();
            activeAggregates.get().remove(id().toString(), this);
        } else if (waitingForBatchEnd.compareAndSet(false, true)) {
            DeserializingMessage.whenBatchCompletes(this::whenBatchCompletes);
        }
    }

    protected void whenBatchCompletes(Throwable error) {
        waitingForBatchEnd.set(false);
        commit();
        activeAggregates.get().remove(id().toString(), this);
    }

    @Override
    public Entity<T> commit() {
        if (committing) {
            commitPending = true;
            return this;
        }
        try {
            committing = true;
            commitPending = false;
            uncommitted.addAll(applied);
            applied.clear();
            lastStable = delegate;
            List<AppliedEvent> events = new ArrayList<>(uncommitted);
            uncommitted.clear();
            var before = lastCommitted;
            lastCommitted = lastStable;
            commitHandler.handle(lastStable, events, before);
        } finally {
            committing = false;
        }
        while (commitPending) {
            commit();
        }
        return this;
    }

    @Override
    public Collection<? extends Entity<?>> entities() {
        return super.entities().stream().map(e -> new ModifiableEntity<>(e, this)).collect(Collectors.toList());
    }

    @Override
    public Entity<T> previous() {
        Entity<T> previous = getDelegate().previous();
        return previous == null ? null : new ModifiableEntity<>(previous, this);
    }

    @FunctionalInterface
    public interface CommitHandler {
        void handle(Entity<?> model, List<AppliedEvent> unpublished, Entity<?> beforeUpdate);
    }

}
