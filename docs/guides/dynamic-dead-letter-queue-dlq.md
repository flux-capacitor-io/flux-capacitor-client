## Dynamic Dead Letter Queue (DLQ)

The **error log is durable** and **replayable**, which means you can treat it as a **powerful, dynamic DLQ**.

Here’s how:

1. **Deploy a special consumer** that tracks the error log.
2. Use `@Trigger` to access and inspect failed messages.
3. Filter and replay failures based on time, payload type, or originating app.

### Example: Retrying Failed Commands from the Past

Let’s assume a bug caused command processing to fail in January 2024. The following setup reprocesses those failed
commands:

```java

@Consumer(name = "command-dlq",
        minIndex = 111677748019200000L, maxIndexExclusive = 111853279641600000L) // 2024-01-01 to 2024-02-01
class CommandReplayHandler {

    @HandleError
    @Trigger(messageType = MessageType.COMMAND)
    void retry(MyCommand failed) {
        FluxCapacitor.sendCommand(failed);
    }
}
```

> ✅ The original `MyCommand` payload is restored and retried transparently.

> 🧠 You can combine this with logic that deduplicates, transforms, or **selectively suppresses** retries.

### When to Use the Error Log

| Use Case                       | How the Error Log Helps              |
|--------------------------------|--------------------------------------|
| 🛠 Fix a bug retroactively     | Replay failed commands from the past |
| 🚧 Validate new handler logic  | Test it against real-world errors    |
| 🔁 Retry transient failures    | Re-issue requests with retry logic   |
| 🧹 Clean up or suppress errors | Filter out known false-positives     |

The error log acts as a **time-travel debugger** — it gives you full control over how and when to address failures, now
or in the future.

---

### Routing with `@RoutingKey`

In Flux Capacitor, routing is used to assign messages to **segments** using consistent hashing. This ensures that
messages about the same entity — for example, all events for a given `OrderId` — are always handled by the **same
consumer**, in **the correct order**.

This is critical when you're handling messages **in parallel**, but still want to ensure **per-entity consistency**.

#### Declaring the Routing Key

By default, the routing key is derived from the message ID. But you can override this by annotating a field, getter, or
method in your **payload class** with `@RoutingKey`.

```java
public record ShipOrder(@RoutingKey OrderId orderId) {
}
```

Or explicitly reference a nested property:

```java

@RoutingKey("customer/id")
public record OrderPlaced(Customer customer) {
}
```

This instructs Flux to extract `customer.id` and use it as the routing key when publishing or consuming the message.

#### Handler-Level Routing Keys

In more advanced cases, you may want to **override routing at the handler level**, regardless of how the message was
published. You can place `@RoutingKey(...)` on the handler method itself:

```java

@HandleEvent
@RoutingKey("organisationId")
void handle(OrganisationUpdate event) {
    // Will route based on organisationId in metadata or payload
}
```

> ⚠️ When doing this, be sure to declare your consumer with `ignoreSegment = true`. Otherwise, this routing override
> may cause certain messages to be silently skipped.

```java

@Consumer(ignoreSegment = true)
public class OrganisationHandler {
    ...
}
```

#### Metadata-Based Routing

Routing keys can also be extracted from **message metadata**. For example:

```java

@RoutingKey("userId")
public class AuditLogEntry { ...
}
```

This will first try to extract `userId` from metadata, and fall back to the payload if not present.

#### Summary

| Placement      | Meaning                                                               |
|----------------|-----------------------------------------------------------------------|
| Field/getter   | Use the property's value as routing key                               |
| Class-level    | Use the named property in metadata or payload                         |
| Handler method | Overrides routing key used during handling (requires `ignoreSegment`) |