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

import io.fluxcapacitor.common.Pair;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.Invocation;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
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
import static io.fluxcapacitor.javaclient.modeling.EventPublication.DEFAULT;
import static io.fluxcapacitor.javaclient.modeling.EventPublication.IF_MODIFIED;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ModifiableAggregateRoot<T> extends DelegatingEntity<T> implements AggregateRoot<T> {

    private static final ThreadLocal<Map<Object, ModifiableAggregateRoot<?>>> activeAggregates =
            ThreadLocal.withInitial(LinkedHashMap::new);

    @SuppressWarnings("unchecked")
    public static <T> Optional<ModifiableAggregateRoot<T>> getIfActive(Object aggregateId) {
        return ofNullable((ModifiableAggregateRoot<T>) activeAggregates.get().get(aggregateId));
    }

    public static Map<String, Class<?>> getActiveAggregatesFor(@NonNull Object entityId) {
        return activeAggregates.get().values().stream().filter(a -> a.getEntity(entityId).isPresent())
                .collect(toMap(e -> e.id().toString(), Entity::type));
    }

    public static <T> Entity<T> load(
            Object aggregateId, Supplier<Entity<T>> loader, boolean commitInBatch, EventPublication eventPublication,
            EntityHelper entityHelper, Serializer serializer, DispatchInterceptor dispatchInterceptor,
            CommitHandler commitHandler) {
        return ModifiableAggregateRoot.<T>getIfActive(aggregateId).orElseGet(
                () -> new ModifiableAggregateRoot<>(loader.get(), commitInBatch, eventPublication,
                                                    entityHelper, serializer, dispatchInterceptor, commitHandler));
    }

    private Entity<T> lastCommitted;
    private Entity<T> lastStable;
    private final boolean commitInBatch;

    private final EventPublication eventPublication;
    private final EntityHelper entityHelper;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final CommitHandler commitHandler;

    private final AtomicBoolean waitingForHandlerEnd = new AtomicBoolean(), waitingForBatchEnd = new AtomicBoolean();
    private final List<DeserializingMessage> applied = new ArrayList<>(), uncommitted = new ArrayList<>();
    private final List<Pair<Message, Boolean>> queued = new ArrayList<>();

    private volatile boolean applying;

    protected ModifiableAggregateRoot(Entity<T> delegate, boolean commitInBatch,
                                      EventPublication eventPublication, EntityHelper entityHelper,
                                      Serializer serializer, DispatchInterceptor dispatchInterceptor,
                                      CommitHandler commitHandler) {
        super(delegate);
        this.entityHelper = entityHelper;
        this.lastCommitted = delegate;
        this.lastStable = delegate;
        this.commitInBatch = commitInBatch;
        this.eventPublication = eventPublication;
        this.serializer = serializer;
        this.dispatchInterceptor = dispatchInterceptor;
        this.commitHandler = commitHandler;
    }

    @Override
    public <E extends Exception> Entity<T> assertLegal(Object command) throws E {
        entityHelper.intercept(command, this).forEach(c -> entityHelper.assertLegal(c, this));
        return this;
    }

    @Override
    public Entity<T> assertAndApply(Object payloadOrMessage) {
        entityHelper.intercept(payloadOrMessage, this).forEach(m -> apply(Message.asMessage(m), true));
        return this;
    }

    @Override
    public Entity<T> apply(Message message) {
        entityHelper.intercept(message, this).forEach(m -> apply(Message.asMessage(m), false));
        return this;
    }

    protected Entity<T> apply(Message message, boolean assertLegal) {
        if (applying) {
            queued.add(new Pair<>(message, assertLegal));
            return this;
        }
        if (assertLegal) {
            entityHelper.assertLegal(message, this);
        }
        try {
            applying = true;
            handleUpdate(a -> {
                var eventPublication =
                        entityHelper.applyInvoker(new DeserializingMessage(message, EVENT, serializer), a)
                                .<Apply>map(HandlerInvoker::getMethodAnnotation).map(Apply::eventPublication)
                                .filter(ep -> ep != DEFAULT).orElse(this.eventPublication);

                int hashCodeBefore = eventPublication == IF_MODIFIED ? a.get() == null ? -1 : a.get().hashCode() : -1;

                Entity<T> result = a.apply(message);
                if (switch (eventPublication) {
                    case ALWAYS, DEFAULT -> true;
                    case IF_MODIFIED -> !Objects.equals(a.get(), result.get())
                                        || (result.get() != null && result.get().hashCode() != hashCodeBefore);
                    case NEVER -> false;
                }) {
                    Message intercepted = dispatchInterceptor.interceptDispatch(message, EVENT);
                    if (intercepted == null) {
                        return a;
                    }
                    Message m = intercepted.addMetadata(Entity.AGGREGATE_ID_METADATA_KEY, id().toString(),
                                                        Entity.AGGREGATE_TYPE_METADATA_KEY, type().getName(),
                                                        Entity.AGGREGATE_SN_METADATA_KEY,
                                                        String.valueOf(getDelegate().sequenceNumber() + 1L));
                    var serializedEvent =
                            dispatchInterceptor.modifySerializedMessage(m.serialize(serializer), m, EVENT);
                    if (serializedEvent == null) {
                        return a;
                    }
                    applied.add(new DeserializingMessage(
                            serializedEvent, type -> serializer.convert(m.getPayload(), type), EVENT));
                }
                return result;
            });
        } finally {
            applying = false;
        }
        while (!queued.isEmpty()) {
            Pair<Message, Boolean> value = queued.removeFirst();
            apply(value.getFirst(), value.getSecond());
        }
        return this;
    }

    @Override
    public Entity<T> update(UnaryOperator<T> function) {
        handleUpdate(a -> a.update(function));
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

    protected void handleUpdate(UnaryOperator<Entity<T>> update) {
        boolean firstUpdate = waitingForHandlerEnd.compareAndSet(false, true);
        if (firstUpdate) {
            activeAggregates.get().putIfAbsent(id(), this);
        }
        try {
            delegate = update.apply(getDelegate());
        } finally {
            if (firstUpdate) {
                Invocation.whenHandlerCompletes((r, e) -> whenHandlerCompletes(e));
            }
        }
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
            activeAggregates.get().remove(id(), this);
            commit();
        } else if (waitingForBatchEnd.compareAndSet(false, true)) {
            DeserializingMessage.whenBatchCompletes(this::whenBatchCompletes);
        }
    }

    protected void whenBatchCompletes(Throwable error) {
        waitingForBatchEnd.set(false);
        activeAggregates.get().remove(id(), this);
        commit();
    }

    @Override
    public Entity<T> commit() {
        uncommitted.addAll(applied);
        applied.clear();
        lastStable = delegate;
        List<DeserializingMessage> events = new ArrayList<>(uncommitted);
        uncommitted.clear();
        commitHandler.handle(lastStable, events, lastCommitted);
        lastCommitted = lastStable;
        return this;
    }

    @FunctionalInterface
    public interface CommitHandler {
        void handle(Entity<?> model, List<DeserializingMessage> unpublished, Entity<?> beforeUpdate);
    }
}
