## Dispatching Messages

Flux Capacitor provides a unified and transparent way to send messages of all types—**commands**, **events**,
**queries**, **schedules**, **web requests**, **metrics**, and more. All message types are routed through a shared
dispatch
infrastructure, with built-in support for:

- **Location transparency**: Message destinations are resolved dynamically. Handlers may live within the current
  process or in a remote application across the globe—as long as it's connected to the same Flux Platform.
- **Dispatch interceptors** for metadata enrichment, authorization, logging, or suppression.
- **Local-first handling**: If a handler exists in the local process, it will receive the message immediately.
- **Automatic forwarding** to the Flux Platform when no local handlers consume the message.
- **Serialization and correlation**: All messages are serialized, tagged with tracing data, and routed by type and
  topic.

### Sending a Message

The easiest way to dispatch messages is via static methods on the `FluxCapacitor` class:

[//]: # (@formatter:off)
```java
FluxCapacitor.sendCommand(new CreateUser("Alice"));  // Asynchronously send a command
FluxCapacitor.queryAndWait(new GetUserById("user-123"));  // Query and block for response
FluxCapacitor.publishEvent(new UserSignUp(...));  // Fire-and-forget event
FluxCapacitor.schedule(new RetryPayment(...), Duration.ofMinutes(5));  // Delayed message
```
[//]: # (@formatter:on)

Each message can also include optional metadata:

[//]: # (@formatter:off)
```java
FluxCapacitor.sendCommand(new CreateUser("Bob"),
                          Metadata.of("source","admin-ui"));
```
[//]: # (@formatter:on)

> ⚠️ Messages are **not routed to a specific destination**. Instead, any number of matching handlers may receive the
> message, whether they run locally or remotely. Flux Capacitor abstracts the transport so that handler location is
> irrelevant.

For a deeper look at message handling, see the [Message Handling](#message-handling) section.

---

### Commands

Commands are used to trigger state changes or domain logic. They may return a result or be fire-and-forget.

**Send and forget:**

[//]: # (@formatter:off)
```java
FluxCapacitor.sendAndForgetCommand(new CreateUser("Alice"));
```
[//]: # (@formatter:on)

**Send and wait (async or blocking):**

[//]: # (@formatter:off)
```java
CompletableFuture<UserId> future
        = FluxCapacitor.sendCommand(new CreateUser("Bob"));

UserId id = FluxCapacitor.sendCommandAndWait(
        new CreateUser("Charlie"));
```
[//]: # (@formatter:on)

---

### Queries

Queries retrieve data from read models or projections.

**Async:**

[//]: # (@formatter:off)
```java
CompletableFuture<UserProfile> result = FluxCapacitor.query(new GetUserProfile("user123"));
```
[//]: # (@formatter:on)

**Blocking:**

[//]: # (@formatter:off)
```java
UserProfile profile = FluxCapacitor.queryAndWait(new GetUserProfile("user456"));
```
[//]: # (@formatter:on)

---

### Events

Events can be published via:

[//]: # (@formatter:off)
```java
FluxCapacitor.publishEvent(new UserLoggedIn("user789"));
```
[//]: # (@formatter:on)

By default:

- ✅ **Events are persisted** in the global event log, making them available for downstream processing, projections, or
  analytics.
- ⚠️ **Exception:** If a **local handler** exists the event will not be forwarded or stored, unless
  `@LocalHandler(logMessage = true)`.

> For aggregate-related domain events, use `Entity#apply(...)` instead. This ensures the event is applied to the entity
> and conditionally published and logged, depending on the aggregate configuration.

---

### Schedules

You can schedule messages or commands for later delivery:

[//]: # (@formatter:off)
```java
// Schedule a message to be handled in 5 minutes using @HandleSchedule
FluxCapacitor.schedule(new ReminderFired(), Duration.ofMinutes(5));

// Fire a periodic schedule every hour
FluxCapacitor.schedulePeriodic(new PollExternalApi());
```
[//]: # (@formatter:on)

---

### Web Requests

Send an outbound HTTP call via the proxy mechanism in Flux Platform:

[//]: # (@formatter:off)
```java
WebRequest request = WebRequest
        .get("https://api.example.com/data").build();
WebResponse response = FluxCapacitor.get()
        .webRequestGateway().sendAndWait(request);
```
[//]: # (@formatter:on)

---

### Metrics

You can publish custom metrics to the Flux Platform:

[//]: # (@formatter:off)
```java
FluxCapacitor.publishMetrics(
        new SystemLoadMetric(cpu, memory));
```
[//]: # (@formatter:on)

---

### What Happens After You Dispatch?

Once you dispatch a message using any of the above methods, Flux goes through the following pipeline:

#### 1. **Dispatch Interceptors (Pre-Serialization)**

Before the message is serialized, all applicable `DispatchInterceptor`s are given a chance to:

- Inject metadata (e.g. correlation IDs, timestamps)
- Modify or validate the message
- Suppress or block dispatch entirely

#### 2. **Local Handlers**

If any handlers (e.g. `@HandleCommand`, `@HandleQuery`) are registered for this message type and topic, they will be
invoked **locally**.

- If the handler is annotated with `@LocalHandler(logMessage = true)`, the message is still forwarded to the Flux
  Platform.
- If the handler **fully consumes** the message, it won't be forwarded.

#### 3. **Serialization**

The message is converted to a `SerializedMessage`, typically using Jackson. This is where versioning and up/downcasting
may apply.

#### 4. **Dispatch Interceptors (Post-Serialization)**

After serialization, interceptors may again modify the message—for example, to inject correlation headers based on the
serialized form.

#### 5. **Forwarding to Flux Platform**

If no local handler consumes the message, it is published to the Flux Platform via the configured `Client` (e.g.
`WebSocketClient`).

- Topics and routing are determined by message type and metadata
- Platform-side logic (like deduplication, retries, rate limits) may apply

---

### Request Timeouts

The `@Timeout` annotation allows developers to specify how long Flux should wait for a **command** or **query** to
complete when using synchronous (`sendAndWait`) APIs.

This is especially useful for time-sensitive interactions where you want to fail fast or have specific SLA expectations
for certain request types.

### Usage

Apply `@Timeout` to a **payload class** (typically a command or query):

[//]: # (@formatter:off)
```java
@Timeout(value = 3, timeUnit = TimeUnit.SECONDS)
public record CalculatePremium(UserProfile profile) implements Request<BigDecimal> {}
```
[//]: # (@formatter:on)

When this message is sent using a blocking gateway call, the configured timeout is respected:

[//]: # (@formatter:off)
```java
BigDecimal result = FluxCapacitor
        .sendAndWait(new CalculatePremium(user));
```
[//]: # (@formatter:on)

If the timeout elapses before a response is received, a `TimeoutException` is thrown.

### Notes

- The timeout only applies to blocking calls (e.g., `sendAndWait`). If you use non-blocking `send(...)` methods that
  return a `CompletableFuture`, **no timeout is enforced** unless you manually attach one:

  [//]: # (@formatter:off)
  ```java
  CompletableFuture<BigDecimal> future
        = FluxCapacitor.send(new CalculatePremium(user));
  future.orTimeout(3, TimeUnit.SECONDS);
  ```
  [//]: # (@formatter:on)

- The default timeout for blocking operations (when `@Timeout` is not present) is **1 minute**.
- To configure timeouts for `WebRequest` calls, use `WebRequestSettings#timeout`.