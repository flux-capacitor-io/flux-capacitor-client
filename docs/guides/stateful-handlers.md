## Stateful Handlers

While aggregates represent domain entities, Flux also supports long-lived **stateful handlers** for modeling workflows,
external interactions, or background processes that span multiple messages.

To declare a stateful handler, annotate a class with `@Stateful`:

```java

@Stateful
public record PaymentProcess(@EntityId String paymentId,
                             @Association String pspReference,
                             PaymentStatus status) {

    @HandleEvent
    static PaymentProcess on(PaymentInitiated event) {
        String pspRef = FluxCapacitor.sendCommandAndWait(new ExecutePayment(...));
        return new PaymentProcess(event.getPaymentId(), pspRef, PaymentStatus.PENDING);
    }

    @HandleEvent
    PaymentProcess on(PaymentConfirmed event) {
        //pspReference property in PaymentConfirmed is matched
        return withStatus(PaymentStatus.CONFIRMED);
    }
}
```

### Key Properties

- `@Stateful` classes persist their state using Flux’s document store (or a custom `HandlerRepository`)
- They are automatically invoked when messages match their associations (`@Association` fields or methods)
- Matching is dynamic and supports multiple handler instances per message
- Multiple handler methods can exist for different message types
- Handlers are immutable by convention — they are updated by returning a new version of themselves
- Returning `null` deletes the handler (useful for terminating flows)

> ℹ️ Like other handlers, stateful handlers may be annotated with `@Consumer` for tracking isolation.

```java

@HandleEvent
PaymentProcess on(PaymentFailed event) {
    return null; // remove from store
}
```

### Matching via Association

Handlers are selected based on one or more `@Association` fields. When a message with a matching association is
published, the handler is loaded and invoked.

```java

@Association
String pspReference;
```

> Note: This is similar to correlation IDs or saga keys — but built-in and fully indexed.

### State Update Semantics

- If the handler method returns a new instance of its class, it replaces the previous version in the store
- If it returns `void` or a value of another type, state is left unchanged
- This allows safe utility returns (like `Duration` for `@HandleSchedule`)

```java

@HandleSchedule
Duration on(CheckStatus schedule) {
    // Return next delay (but don’t update handler state)
    return Duration.ofMinutes(5);
}
```

### Batch Commit Control

By default, changes to a `@Stateful` handler are persisted immediately. Set `commitInBatch = true` to defer updates
until the current message batch completes. Flux will ensure that:

- Newly created handlers are matched by subsequent messages
- Deleted handlers won’t receive more messages in the batch
- Updates are consistent within the batch

> This dramatically improves performance for high-throughput workflows.

### Indexing Support

Stateful handlers are automatically `@Searchable`. You can configure:

- A custom collection name
- Time-based indexing fields (e.g. `timestampPath` or `endPath`)

This allows you to query, filter, and monitor stateful handlers using Flux’s search API — covered in the next section.

---

Stateful handlers are ideal for:

- **Workflows** and **Sagas**
- **Pollers**, **reminders**, and **background jobs**
- External **API orchestrations**
- **Process managers** (e.g., order fulfillment, payment retry, etc.)

They complement aggregates without competing with them — and allow modeling temporal behavior in a clean, event-driven
way.