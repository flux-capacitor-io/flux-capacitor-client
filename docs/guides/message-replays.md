## Message Replays

Flux Capacitor allows you to **replay past messages** by tracking from an earlier index in the message log.  
This is useful for:

- Rebuilding projections or read models
- Repairing missed or failed messages
- Retrospectively introducing new functionality

Since message logs — including **commands**, **events**, **errors**, etc. — are durably stored (with **events** retained
indefinitely by default), replays are usually available.

#### Replaying from the Past

The most common way to initiate a replay is to define a **new consumer** using the `@Consumer` annotation with a
`minIndex`.

```java

@Consumer(name = "auditReplay", minIndex = 111677748019200000L)
public class AuditReplayHandler {

    @HandleEvent
    void on(CreateUser event) {
        ...
    }
}
```

In this example, the consumer `auditReplay` will process all events starting from **January 1st, 2024**.

> ℹ️ The `minIndex` value specifies the *inclusive* starting point for the message log.  
> Index values are based on time and can be derived using `IndexUtils`:

```java
long index = IndexUtils.indexFromTimestamp(
        Instant.parse("2024-01-01T00:00:00Z"));
// returns: 111677748019200000L
```

This approach is perfect for:

- Starting **fresh consumers** for replays
- Bootstrapping projections without interfering with live handlers
- Keeping logic encapsulated and isolated

#### Resetting Existing Consumers

If you want to reset an existing consumer to an earlier point in the log:

[//]: # (@formatter:off)
```java
long replayIndex = 111677748019200000L;
FluxCapacitor.client()
    .getTrackingClient(MessageType.EVENT)
    .resetPosition("myConsumer", replayIndex, Guarantee.STORED);
```
[//]: # (@formatter:on)

This restarts the consumer from the given index and causes old messages to be redelivered and reprocessed.

> ⚠️ Replaying over existing handlers may require extra caution (e.g. to avoid duplicating side effects).

#### 🔁 Parallel Replays with `exclusive = false`

Sometimes you want to **re-use the same handler class** for both:

- **Live processing** (e.g. default consumer)
- **Replaying past messages** (e.g. rebuilding projections)

To achieve this, annotate the handler with `exclusive = false`. This tells Flux that the handler may participate in
**multiple consumers simultaneously**:

```java

@Consumer(name = "live", exclusive = false)
public class OrderProcessor {
    @HandleCommand
    void handle(SendOrder command) {
        //submit an order
    }
}
```

Now, register a **second consumer** at runtime for the **same handler** using a different tracking position:

[//]: # (@formatter:off)
```java
fluxCapacitorBuilder.addConsumerConfiguration(
    ConsumerConfiguration.builder()
        .name("replay") // A new consumer
        .handlerFilter(handler -> handler instanceof OrderProcessor) // same class
        .minIndex(111677748019200000L) // Start of replay window
        .maxIndex(111853279641600000L) // End of replay window
        .build(),
    MessageType.COMMAND
);
```
[//]: # (@formatter:on)

This will spin up a **parallel tracker** that replays all commands since **2024-01-01T00:00:00Z**
until **2024-02-01T00:00:00Z**, while the original `live` consumer continues uninterrupted. In this example, orders
sent during the month of January 2024 will be resubmitted.

> ✅ This is ideal for **bootstrapping read models** or **running repair jobs** without affecting production flow
> or duplicating a handler class.

---

### Handling Failures by Replaying from the Error Log

Flux Capacitor automatically logs **all message handling errors** to a dedicated **error log**. This includes failures
for:

- Commands
- Queries
- WebRequests
- Schedule messages
- Events (if a handler throws)
- And more

Each error message includes detailed **stack traces**, **metadata**, and most importantly, a **reference to the original
message** that caused the failure.

### Error Handling with `@HandleError`

To react to failures programmatically, define a handler method with the `@HandleError` annotation:

```java

@HandleError
void onError(Throwable error) {
    log.warn("Something went wrong!", error);
}
```

You can inject the **failed message** using the `@Trigger` annotation:

```java

@HandleError
void onError(Throwable error, @Trigger SomeCommand failedCommand) {
    log.warn("Failed to process {}: {}", failedCommand, error.getMessage());
}
```

> ℹ️ The trigger can be the payload, or a full `Message` or `DeserializingMessage`.

You can also **filter** error handlers by trigger type, message type, or originating consumer:

```java

@HandleError
@Trigger(messageType = MessageType.COMMAND, consumer = "my-app")
void retryFailedCommand(MyCommand failed) {
    FluxCapacitor.sendCommand(failed);
}