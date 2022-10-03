package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.handling.Invocation;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.util.Optional.ofNullable;

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ModifiableAggregateRoot<T> extends DelegatingEntity<T> {

    private static final ThreadLocal<Map<Object, ModifiableAggregateRoot<?>>> activeAggregates =
            ThreadLocal.withInitial(HashMap::new);

    @SuppressWarnings("unchecked")
    public static <T> Optional<ModifiableAggregateRoot<T>> getIfActive(String aggregateId) {
        return ofNullable((ModifiableAggregateRoot<T>) activeAggregates.get().get(aggregateId));
    }

    public static <T> ModifiableAggregateRoot<T> load(
            String aggregateId, Supplier<ImmutableEntity<T>> loader, boolean commitInBatch,
            Serializer serializer, DispatchInterceptor dispatchInterceptor, CommitHandler commitHandler) {
        return ModifiableAggregateRoot.<T>getIfActive(aggregateId).orElseGet(
                () -> new ModifiableAggregateRoot<>(
                        loader.get(), commitInBatch, serializer, dispatchInterceptor, commitHandler));
    }

    private Entity<T> lastCommitted;
    private Entity<T> lastStable;

    private final boolean commitInBatch;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final CommitHandler commitHandler;

    private final AtomicBoolean waitingForHandlerEnd = new AtomicBoolean(), waitingForBatchEnd = new AtomicBoolean();
    private final List<DeserializingMessage> applied = new ArrayList<>(), uncommitted = new ArrayList<>();
    private final List<Message> queued = new ArrayList<>();

    private volatile boolean applying;

    protected ModifiableAggregateRoot(ImmutableEntity<T> delegate, boolean commitInBatch,
                                      Serializer serializer, DispatchInterceptor dispatchInterceptor,
                                      CommitHandler commitHandler) {
        super(delegate);
        this.lastCommitted = delegate;
        this.lastStable = delegate;
        this.commitInBatch = commitInBatch;
        this.serializer = serializer;
        this.dispatchInterceptor = dispatchInterceptor;
        this.commitHandler = commitHandler;
    }

    @Override
    public ModifiableAggregateRoot<T> apply(Message message) {
        if (applying) {
            queued.add(message);
            return this;
        }
        Message m = dispatchInterceptor.interceptDispatch(message, EVENT)
                .addMetadata(Entity.AGGREGATE_ID_METADATA_KEY, id(),
                             Entity.AGGREGATE_TYPE_METADATA_KEY, type().getName());
        DeserializingMessage eventMessage = new DeserializingMessage(
                dispatchInterceptor.modifySerializedMessage(m.serialize(serializer), m, EVENT),
                type -> serializer.convert(m.getPayload(), type), EVENT);
        try {
            applying = true;
            handleUpdate(a -> {
                Entity<T> result = a.apply(eventMessage);
                applied.add(eventMessage);
                return result;
            });
        } finally {
            applying = false;
        }
        while (!queued.isEmpty()) {
            apply(queued.remove(0));
        }
        return this;
    }

    @Override
    public ModifiableAggregateRoot<T> update(UnaryOperator<T> function) {
        handleUpdate(a -> a.update(function));
        return this;
    }

    @Override
    public Collection<? extends Entity<?>> entities() {
        return super.entities().stream().map(e -> new ModifiableEntity<>(e, this)).collect(Collectors.toList());
    }

    @Override
    public Entity<T> previous() {
        Entity<T> previous = delegate.previous();
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
            applied.clear();
            lastStable = getDelegate();
            if (!commitInBatch) {
                commit();
            } else if (waitingForBatchEnd.compareAndSet(false, true)) {
                DeserializingMessage.whenBatchCompletes(e -> commit());
            }
        } else {
            applied.clear();
            delegate = lastStable;
            if (!commitInBatch) {
                activeAggregates.get().remove(id(), this);
            } else if (waitingForBatchEnd.compareAndSet(false, true)) {
                DeserializingMessage.whenBatchCompletes(e -> commit());
            }
        }
    }

    protected void commit() {
        activeAggregates.get().remove(id(), this);
        List<DeserializingMessage> events = new ArrayList<>(uncommitted);
        uncommitted.clear();
        waitingForBatchEnd.set(false);
        commitHandler.handle(lastStable, events, lastCommitted);
        lastCommitted = lastStable;
    }

    @FunctionalInterface
    public interface CommitHandler {
        void handle(Entity<?> model, List<DeserializingMessage> unpublished, Entity<?> beforeUpdate);
    }
}
