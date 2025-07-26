## User-defined message logs

Flux Capacitor supports **custom message logs** in addition to built-in ones like commands, events, and queries.

Most applications won't need custom message logs — but they can be very useful in advanced scenarios:

- To **track external systems or integrations**, while keeping the main event log clean.
- To **store batches of updates** for external consumers that poll infrequently.
- To **segment logic** or apply different retention guarantees per topic.

Flux Capacitor lets you publish and handle custom messages by assigning them to **user-defined topics**. These messages
are written to their own durable logs and can be tracked just like commands or events.

### Custom Handlers

To receive messages from a custom log, annotate your method with `@HandleCustom` and specify the topic name:

```java

@HandleCustom("metering-points")
void on(MeteringPoint event) {
    log.info("Metering point: {}", event);
}
```

Like other handlers, this can be:

- **Stateless or stateful**
- Attached to a **custom consumer**
- Used to **return a result** if the message is a request
- **Passive**, meaning it won’t emit result messages

> ✅ Custom logs are fully integrated with Flux tracking and delivery infrastructure.

### 📬 Publishing to a Custom Log

You can publish messages manually to any custom topic:

[//]: # (@formatter:off)
```java
FluxCapacitor.get()
    .customGateway("third-party-events")
    .sendAndForget(new AuditEntry("User login"));
```
[//]: # (@formatter:on)

This makes it easy to introduce new asynchronous workflows, specialized event types, or low-frequency control signals.

### 🧹 Setting Retention Time

Each message log in Flux retains messages independently. You can configure how long messages in your **custom log**
should be retained:

[//]: # (@formatter:off)
```java
FluxCapacitor.get()
    .customGateway("third-party-events")
    .setRetentionTime(Duration.ofDays(90));
```
[//]: # (@formatter:on)

Retention can also be set for built-in logs like commands or results — though it's advisable to configure it only
for custom topics.