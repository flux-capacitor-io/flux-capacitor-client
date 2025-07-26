## Model Persistence

Flux Capacitor supports multiple strategies for storing and reloading aggregates:

- **Event sourcing**: state is derived by replaying a stream of applied updates (events)
- **Document storage**: the full aggregate is stored as a document
- **In-memory only**: ephemeral state, not persisted across messages

By default, `@Aggregate` uses **event sourcing** (`@Aggregate(eventSourced = true)` by default), but you can configure
each aggregate individually.

---

### Event Sourcing

Event-sourced aggregates are reconstructed from their event history. When you load an aggregate
(e.g. via `loadAggregate(...)`, `loadEntity(...)`, or `loadAggregateFor(...)`), Flux uses the following strategy to
restore its current state:

---

### 1️⃣ Loading an Aggregate

Flux Capacitor will attempt to resolve the **current state** of the aggregate or entity as follows:

- **From cache**, if caching is enabled (default behavior).
- If **snapshotting** is enabled and a snapshot is available, Flux uses it as the starting point and then replays any
  subsequent events.
- Otherwise, the entire state is rehydrated from the **event history**, by replaying each past event through its
  matching `@Apply` method.

Each event is **deserialized** and routed to the corresponding `@Apply` method to reconstruct the aggregate's entity
graph.

- If no such method exists for a given event, the event is silently **ignored**.
- However, if the **event class itself is missing**, deserialization will fail unless `ignoreUnknownEvents = true` is
  set on the aggregate. For better ways to deal with this, see [Upcasting](#upcasting).

---

### 2️⃣ Applying Updates and Committing Changes

Once an aggregate has been loaded, you can apply updates, e.g.: using `Entity#apply(...)`. Each update follows this
lifecycle:

1. The state transition is computed using an `@Apply` method (on the update or entity).
2. The update is **stored** in the event store and optionally **published** (depending on publication settings).
3. The new state is written back to the **aggregate cache** (if enabled).
4. A new **snapshot** is created, if a snapshot threshold has been reached.

> **Commit Timing:**  
> By default, updates are committed only **after the current message batch completes**, not immediately. This means:
>
> - Updates are **locally cached** (per tracker thread) until the batch is confirmed.
> - This avoids unnecessary round-trips to the Flux Platform during batch processing.
>
> You can change this behavior by explicitly committing the update earlier—i.e., at the end of the current handler
> method.

#### Persistence Behavior

You can customize event persistence behavior with:

- `eventPublication`: prevent events when nothing has changed
- `publicationStrategy`: store-only vs publish-and-store
- `snapshotPeriod`: replace snapshot after every N updates
- `ignoreUnknownEvents`: handle versioned aggregates gracefully

Here’s a simple example:

```java

@Aggregate(snapshotPeriod = 1000)
public record UserAccount(@EntityId UserId userId,
                          UserProfile profile) {

    @Apply
    UserAccount apply(UpdateProfile update) {
        return toBuilder().profile(update.getProfile()).build();
    }
}
```

### Document Storage

Flux Capacitor also supports storing aggregates as documents in a searchable document store. This is useful for:

- Read-heavy aggregates
- Aggregates with large histories
- Reference models that don’t need event streams

To enable document storage, set `searchable = true` in the `@Aggregate` annotation:

```java

@Aggregate(eventSourced = false, searchable = true, collection = "countries")
public record Country(@EntityId String countryCode,
                      String name) {
}
```

This stores the entire aggregate as a document in the `"countries"` collection. The entity can still use:

- `@InterceptApply` to block or modify updates
- `@AssertLegal` to validate updates
- `@Apply` to compute and update state

Each applied update will overwrite the document in the store, and—by default—**will also be published as an event**. If
that's not desirable, you can disable event publication for the aggregate using
`@Aggregate(eventPublication = NEVER)`. However, if you do, make sure to **also disable caching**, or you risk ending up
with **inconsistent state** between application instances.

> ℹ️ The `collection` defaults to the simple class name of the aggregate if not explicitly set. You can also configure
> time-range indexing with `timestampPath` and `endPath` to enable temporal querying.

> ⚠️ If you set `eventSourced = false` and do **not** enable `searchable`, the aggregate will not be persisted at all.  
> Its state will only live in memory during message processing. This is typically not recommended unless you're using  
> the aggregate for purely transient behavior.

---

### Dual Persistence

You can combine both strategies by enabling both `eventSourced = true` and `searchable = true`.

This causes Flux to:

- Store events for replay and audit purposes
- Index the latest version as a document for fast retrieval and search

```java

@Aggregate(searchable = true)
public record Order(@EntityId OrderId orderId,
                    OrderDetails details) {
}
```

This hybrid approach is ideal when you need both traceability and query speed.

---

### Caching and Checkpoints

Flux Capacitor automatically caches aggregates after loading or applying updates (unless `cached = false`). This allows:

- Fast reuse of recently loaded aggregates
- Automatic rehydration from snapshots or partial checkpoints (when configured)

You can tune cache behavior with:

- `cached`: disable shared cache entirely
- `cachingDepth`: how many versions to retain (enables `.previous()` access)
- `checkpointPeriod`: how often to insert intermediate event checkpoints

> ✅ When loading an aggregate inside an event handler, Flux ensures that the returned entity is always up-to-date. If
> the event being handled is part of that aggregate, the aggregate is automatically rehydrated
> *up to and including* the current event. **Flux will wait** (if needed) until the aggregate cache has caught up to
> that
> point, ensuring consistency and preventing stale reads — even during concurrent or out-of-order processing.

This makes it possible to write event-sourced, state-aware logic directly within event handlers — often eliminating the
need for separate projections or read models.

#### Example: Detecting Significant Balance Change

```java
public class FraudMonitor {

    @HandleEvent
    void handle(Entity<BankAccount> entity) {
        BankAccount current = entity.get();
        BankAccount previous = entity.previous().get();

        if (hasSuspiciousDelta(previous, current)) {
            FluxCapacitor.publishEvent(new AdminNotification(
                    "Unusual balance change on account %s"
                            .formatted(current.getAccountId())));
        }
    }

    boolean hasSuspiciousDelta(BankAccount previous, BankAccount current) {
        if (previous == null || current == null) {
            return false;
        }
        BigDecimal delta = current.getBalance()
                .subtract(previous.getBalance()).abs();
        return delta.compareTo(BigDecimal.valueOf(10_000)) > 0;
    }
}
```

In this example:

- The aggregate (`BankAccount`) is automatically loaded in-sync with the current event being handled.
- The handler has access to both the **current state** and the **previous state** of the entity.
- It uses this to decide whether a significant balance change has occurred.
- No external store or manual query is needed — this is pure, consistent, event-sourced state.