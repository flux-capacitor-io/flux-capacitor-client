package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.util.Optional.ofNullable;

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ModifiableAggregateRoot<T> extends DelegatingAggregateRoot<T, ImmutableAggregateRoot<T>> {
    
    private static final ThreadLocal<Map<String, ModifiableAggregateRoot<?>>> activeAggregates = 
            ThreadLocal.withInitial(HashMap::new);

    @SuppressWarnings("unchecked")
    public static <T> ModifiableAggregateRoot<T> load(String aggregateId, Supplier<ModifiableAggregateRoot<T>> loader) {
        return ofNullable((ModifiableAggregateRoot<T>) activeAggregates.get().get(aggregateId)).orElseGet(loader);
    }

    private ImmutableAggregateRoot<T> lastStable;
    
    private final boolean commitInBatch;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final BiConsumer<ImmutableAggregateRoot<?>, List<DeserializingMessage>> commitHandler;

    private final AtomicBoolean waitingForHandlerEnd = new AtomicBoolean(), waitingForBatchEnd = new AtomicBoolean();
    private final List<DeserializingMessage> applied = new ArrayList<>(), uncommitted = new ArrayList<>();

    public ModifiableAggregateRoot(ImmutableAggregateRoot<T> delegate, boolean commitInBatch, Serializer serializer,
                                   DispatchInterceptor dispatchInterceptor,
                                   BiConsumer<ImmutableAggregateRoot<?>, List<DeserializingMessage>> commitHandler) {
        this(delegate, delegate, commitInBatch, serializer, dispatchInterceptor, commitHandler);
    }

    public ModifiableAggregateRoot(ImmutableAggregateRoot<T> delegate, ImmutableAggregateRoot<T> lastStable,
                                   boolean commitInBatch, Serializer serializer, DispatchInterceptor dispatchInterceptor,
                                   BiConsumer<ImmutableAggregateRoot<?>, List<DeserializingMessage>> commitHandler) {
        super(delegate);
        this.lastStable = lastStable;
        this.commitInBatch = commitInBatch;
        this.serializer = serializer;
        this.dispatchInterceptor = dispatchInterceptor;
        this.commitHandler = commitHandler;
    }

    @Override
    public ModifiableAggregateRoot<T> apply(Message message) {
        Message m = dispatchInterceptor.interceptDispatch(message.addMetadata(
                AggregateRoot.AGGREGATE_ID_METADATA_KEY, id(),
                AggregateRoot.AGGREGATE_TYPE_METADATA_KEY, type().getName()), EVENT);
        DeserializingMessage eventMessage = new DeserializingMessage(
                dispatchInterceptor.modifySerializedMessage(m.serialize(serializer), m, EVENT),
                type -> serializer.convert(m.getPayload(), type), EVENT);
        applied.add(eventMessage);
        return handleUpdate(delegate.apply(eventMessage));
    }

    @Override
    public ModifiableAggregateRoot<T> update(UnaryOperator<T> function) {
        return handleUpdate(delegate.update(function));
    }

    private ModifiableAggregateRoot<T> handleUpdate(ImmutableAggregateRoot<T> update) {
        delegate = update;
        try {
            return this;
        } finally {
            if (waitingForHandlerEnd.compareAndSet(false, true)) {
                activeAggregates.get().putIfAbsent(id(), this);
                DeserializingMessage.whenHandlerCompletes(this::whenHandlerCompletes);
            }
        }
    }

    private void whenHandlerCompletes(Throwable error) {
        waitingForHandlerEnd.set(false);
        if (error == null) {
            uncommitted.addAll(applied);
            applied.clear();
            lastStable = delegate;
            if (!commitInBatch) {
                commit();
            } else if (waitingForBatchEnd.compareAndSet(false, true)) {
                DeserializingMessage.whenBatchCompletes(e -> commit());
            }
        } else {
            activeAggregates.get().remove(id(), this);
            applied.clear();
            delegate = lastStable;
        }
    }

    private void commit() {
        activeAggregates.get().remove(id(), this);
        List<DeserializingMessage> events = new ArrayList<>(uncommitted);
        uncommitted.clear();
        waitingForBatchEnd.set(false);
        commitHandler.accept(lastStable, events);
    }
}
