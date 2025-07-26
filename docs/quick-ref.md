# Flux Capacitor — Quick Reference

> Everyday conventions, patterns, and pitfalls in ≤ 60 sec  
> For deeper topics see `/docs/guides/`.

---

## Project layout & dependencies

| Purpose                        | Typical location             |
|--------------------------------|------------------------------|
| Application package            | `com.yourorg.<app>`          |
| Root package                   | `com.yourorg.<app>.<domain>` |
| API (commands / queries / IDs) | `….api`                      |
| Domain model & entities        | `….api.model`                |
| Handlers                       | in root package              |
| Tests                          | `src/test/java`              |

Import the **Flux Capacitor BOM** in Maven/Gradle so every module uses the same version.

---

## Message → Handler map

| Message             | Publish                         | Handle with                                 | Notes                                              |
|---------------------|---------------------------------|---------------------------------------------|----------------------------------------------------|
| **Command** (write) | `FluxCapacitor.sendCommand(…)`  | `@HandleCommand`                            | may return result                                  |
| **Query** (read)    | `FluxCapacitor.queryAndWait(…)` | `@HandleQuery`                              | strongly‑typed via `implements Request<R>`         |
| **Event** (fact)    | `FluxCapacitor.publishEvent(…)` | `@HandleEvent`                              | persisted unless consumed locally                  |
| **Schedule**        | `FluxCapacitor.schedule(…)`     | `@HandleSchedule` / `@Periodic`             | one‑off or recurring; cancel with `cancelSchedule` |
| **WebRequest**      | via gateway                     | `@HandleGet` / `@HandlePost` / `@HandleWeb` | proxied through Flux gateway                       |

> For long‑running workflows (sagas), annotate a handler class with **`@Stateful`**.

### Minimal example

[//]: # (@formatter:off)
```java
record HelloWorld() {}

class HelloWorldHandler {
    @HandleEvent void handle(HelloWorld e) {
        System.out.println("Hello World!");
    }
}

FluxCapacitor.publishEvent(new HelloWorld());
```
[//]: # (@formatter:on)

---

## Payloads are log-agnostic

The same payload type can appear on different logs. Its semantics are determined **solely by where it is written**, not
by its class name.

- **Command** — written to the **command log** and handled by `@HandleCommand`. If the handler returns a value, that
  value is written to the **result log**.
- **Event** — produced by applying an update to an entity (`apply(...)`) or by explicitly publishing. When you apply,
  the payload is appended to the **entity’s event stream** **and** to the **global event log**.
- **Result** — the return value of a handler (command/query/web request), automatically written to the **result log**.
- **Other logs** — queries (`Request<R>`), schedules, web requests, metrics, errors, and user-defined logs follow the
  same principle: **role = destination log**.

> **Local handlers.** A local handler can consume a message without forwarding or storing it. Use
`@LocalHandler(logMessage = true)` when you still want it forwarded/logged.

> **Naming.** Prefer imperative/present-tense payload names (`CreateUser`, `TurnDeviceOn`). Use past tense only if you
> intentionally model a distinct fact type. Flux behavior is driven by the **log**, not by the name.

---

## Routing & consistency

```java
public record ShipOrder(@RoutingKey OrderId orderId) {
}
```

*Messages with the same routing key are processed on the same segment, preserving order.*
*Command parameter **name** must match the `@EntityId` field in entities for automatic routing/binding.*

---

## Testing one‑liners

[//]: # (@formatter:off)
```java
TestFixture fixture = TestFixture.create(new MyHandler());
fixture.whenEvent(new SomethingHappened())
       .expectCommands(new DoSomething());
```
[//]: # (@formatter:on)

Use `.expectOnlyCommands(…)` or `.expectResult(…)` to tighten assertions.

---

## Validation, injection & security

- Add Bean‑Validation annotations (`@NotNull`, `@Size`, …) to payloads – invalid messages never reach handlers.
- Common injected params: `Message`, `Metadata`, entity fields, current `User` (often called `Sender`).
- Restrict handlers with `@RequiresAnyRole`, `@ForbidsAnyRole`, `@RequiresUser`, etc.
- Put package‑level security defaults in `package‑info.java`, override on methods if needed.

---

## Common pitfalls

| ⛔ Don’t                                       | ✅ Do                                                                        |
|-----------------------------------------------|-----------------------------------------------------------------------------|
| Generate IDs inside `@Apply`                  | Generate in endpoint / command using `FluxCapacitor.generateId(Type.class)` |
| Call `System.currentTimeMillis()` in `@Apply` | Inject `Clock` / pass time in message                                       |
| Mismatch command param ↔ entity field names   | Keep them identical for auto‑binding                                        |
| Block on futures in handler thread            | Return value synchronously or move async work outside handler               |

---

## Happy‑path checklist

1. Generate IDs **before** dispatch via `FluxCapacitor.generateId(…)`.
2. Validate payload (JSR‑380) and user roles.
3. Add `@AssertLegal` checks before `@Apply`.
4. Use the routing key annotation on aggregate identifiers and match param names.
5. Cover new handlers with a `TestFixture` unit‑test.
6. For document search enable `@Aggregate(searchable = true)` – see guide.

---

*(Advanced: parameter injection, up/down‑casting, DLQs, document search → see `/docs/index.md` and `/docs/guides`.)*