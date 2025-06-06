<a href="https://flux-capacitor.io">
    <img src="https://flux-capacitor.io/assets/brand/flux-capacitor-white.svg" alt="Flux Capacitor logo" title="Flux Capacitor" align="right" height="60" />
</a>

Flux Capacitor java client
======================

[![Build](https://github.com/flux-capacitor-io/flux-capacitor-client/actions/workflows/deploy.yml/badge.svg)](https://github.com/flux-capacitor-io/flux-capacitor-client/actions)
[![Javadoc](https://img.shields.io/badge/javadoc-main-blue)](https://flux-capacitor.io/flux-capacitor-client/javadoc/apidocs/)
[![Maven Central](https://img.shields.io/maven-central/v/io.flux-capacitor/flux-capacitor-client)](https://central.sonatype.com/artifact/io.flux-capacitor/flux-capacitor-client?smo=true)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)

This repository contains the official Java client for [flux.host](https://flux.host), the Flux Capacitor platform. For a
short
overview of functionalities, check out this [cheatsheet](documentation/cheatsheet.pdf).

Installation
======================

### Maven users

Add these dependencies to your project's POM, replacing `${flux-capacitor.version}` with the latest version, as shown in
the badge above:

[//]: # (@formatter:off)
```xml
<dependency>
    <groupId>io.flux-capacitor</groupId>
    <artifactId>java-client</artifactId>
    <version>${flux-capacitor.version}</version>
</dependency>
<dependency>
    <groupId>io.flux-capacitor</groupId>
    <artifactId>java-client</artifactId>
    <version>${flux-capacitor.version}</version>
    <classifier>tests</classifier>
    <scope>test</scope>
</dependency>
```
[//]: # (@formatter:on)

### Gradle users

Add the following dependencies, replacing `${flux-capacitor.version}` with the latest version, as shown in the badge
above:

```
compile(group: 'io.flux-capacitor', name: 'java-client', version: '${flux-capacitor.version}')
testCompile(group: 'io.flux-capacitor', name: 'java-client', version: '${flux-capacitor.version}', classifier: 'tests')
```

Basic example
======================

Create a new project and add an event class:

```java
package com.example;

class HelloWorld {
}
```

Create a handler for the event:

```java
package com.example;

import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;

class HelloWorldEventHandler {
    @HandleEvent
    void handle(HelloWorld event) {
        System.out.println("Hello World!");
    }
}
```

Publish the event:

```java
package com.example;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.LocalClient;

public class ExampleMain {
    public static void main(final String[] args) {
        FluxCapacitor fluxCapacitor
                = DefaultFluxCapacitor.builder().build(LocalClient.newInstance());
        fluxCapacitor.registerHandlers(new HelloWorldEventHandler());
        fluxCapacitor.eventGateway().publish(new HelloWorld());
    }
}
```

Output:

```
Hello World!
```

### With Spring Boot

Flux Capacitor integrates seamlessly with Spring. Here’s how the above example looks with Spring Boot:

```java

@SpringBootApplication
@Import(FluxCapacitorSpringConfig.class)
public class ExampleMain {
    public static void main(String... args) {
        SpringApplication.run(ExampleMain.class, args);
        FluxCapacitor.publishEvent(new HelloWorld());
    }
}
```

And annotate your handler with `@Component`:

```java

@Component
public class HelloWorldEventHandler {
    @HandleEvent
    void handle(HelloWorld event) {
        System.out.println("Hello World!");
    }
}
```

### 🧪 Testing your handler

Flux Capacitor includes a powerful TestFixture utility for testing your handlers without needing a full application
or infrastructure setup.

Here’s how to test our HelloWorldEventHandler from earlier:

```java
class HelloWorldEventHandlerTest {

    @Test
    void testHelloWorldHandler() {
        TestFixture.create(new HelloWorldEventHandler())
                .whenEvent(new HelloWorld())
                .expectThat(fc -> System.out.println("Event handled successfully!"));
    }
}
```

This will invoke your handler exactly like Flux Capacitor would in production, but entirely in memory and synchronously
by default.

> ✅ We’ll explore more powerful testing patterns — including assertions on results, published commands, exceptions,
> and full event flows — later in this guide.

Features
======================

The java client supports all features of Flux Capacitor but also offers plenty of additional functionality. Here’s a
summary of the most important features:

## 📨 Message Publishing and Handling

Flux Capacitor is centered around sending and receiving messages — such as __commands__, __events__, __queries__, and
__web requests__. These messages can originate from your own application or any other client connected to the same Flux
Platform.

Handlers are simply methods annotated with `@HandleCommand`, `@HandleEvent`, `@HandleQuery`, etc. Here’s a basic example
of an event handler that dispatches a command to send a welcome email when a user is created:

```java
class UserEventHandler {
    @HandleEvent
    void handle(UserCreated event) {
        FluxCapacitor.sendCommand(new SendWelcomeEmail(event.getUserProfile()));
    }
}
```

This handler uses the static `sendCommand` method on FluxCapacitor, which works because the client is automatically
injected into the thread-local context before message handling begins. This approach eliminates the need to inject
`FluxCapacitor` into every handler.

To receive that command, you would define a corresponding command handler:

```java
class EmailCommandHandler {
    @HandleCommand
    void handle(SendWelcomeEmail command) {
        //send welcome email to user
    }
}
```

Handlers can return a result (e.g. from queries or commands), which will automatically be published as a __Result__
message and sent back to the originating client:

```java
class UserQueryHandler {
    @HandleQuery
    UserProfile handle(GetUserProfile query) {
        //return the user profile
    }
}
```

To perform a query and wait for its result synchronously, you can use:

```java
class UserEventHandler {
    @HandleEvent
    void handle(ResetPassword event) {
        UserProfile userProfile = FluxCapacitor.queryAndWait(new GetUserProfile(event.getUserId()));
        // Perform reset using userProfile
    }
}
```

### Handler Matching and Passive Handlers

Flux Capacitor dynamically resolves which handler method(s) should respond to a message based on **handler specificity**
and **message type**. This resolution behavior is consistent across all message types — including **commands**,
**queries**, **events**, **errors**, **metrics**, and more.

#### Most Specific Handler Wins (Per Class)

If multiple handler methods in the *same class* can handle a message (e.g. a `CreateUser` event), **only the most
specific method** is invoked. This allows you to define fallback methods at a more general level if no exact match is
found.

```java

@HandleEvent
void handle(Object event) {
    log.info("Generic fallback");
}

@HandleEvent
void handle(CreateUser event) {
    log.info("Handling specific CreateUser event");
}
```

➡️ In this example, only the `handle(CreateUser)` method runs when a `CreateUser` event is dispatched.

#### Multiple Handler Classes Are Invoked

When the same message is handled by **different classes**, all eligible handlers are invoked independently.

```java
public class BusinessHandler {
    @HandleEvent
    void handle(CreateUser event) {
        // perform business logic
    }
}

public class LoggingHandler {
    @HandleEvent
    void logEvent(Object event) {
        log.info("Observed event {}", event);
    }
}
```

➡️ Here, both handlers are invoked when a `CreateUser` event is dispatched.

#### Requests Prefer a Single Active Handler

For **request-like messages** — such as commands, queries and web requests — only **one handler** class is
expected to produce a response. If multiple are eligible, only the ones marked as **non-passive** will be considered for
producing the result.

Additional handlers may still be registered using `passive = true`, e.g., for logging, auditing, metrics, or outbox
patterns.

```java
public class UserHandler {
    @HandleQuery
    User handle(GetUser query) {
        return userRepository.find(query.getUserId());
    }
}

public class QueryMetricsHandler {
    @HandleQuery(passive = true)
    void record(Object query) {
        metrics.increment("queries." + query.getClass().getSimpleName());
    }
}
```

➡️ In this case:

- The `record(...)` method logs the query,
- The `handle(...)` method produces the result.

#### WebRequest Matching Uses URI Path

While the above rules apply to all message types, **WebRequests** differ slightly: their handler resolution is based on
**URL path matching**, not payload type.

Still, the principle of "most specific handler wins" applies — e.g., a method matching `/users/{id}` is more specific
than one matching `/users/**`.

You can define multiple WebRequest handlers with different paths or HTTP methods in the same class:

```java

@HandleGet(path = "/users/{id}")
User getUser(@Path("id") String userId) {
    return userRepository.find(userId);
}

@HandleGet(path = "/users/**")
List<User> listUsers() {
    return userRepository.findAll();
}
```

---

By combining **handler specificity**, **class-level isolation**, and the `passive` flag, Flux Capacitor gives you
precise control over how messages are processed — even across mixed concerns like logging, read models, business logic,
and cross-cutting concerns.

### Metadata Support

Messages can include **metadata**, which are contextual key-value pairs (typically of a technical nature). These are
useful for passing user context, correlation IDs, request info, etc.

For example, sending a command with metadata:

[//]: # (@formatter:off)
```java
FluxCapacitor.sendCommand(
    new CreateUser(...),
    Metadata.of("userAgent", userAgent)
);
```
[//]: # (@formatter:on)

Reading metadata in a handler is just as easy:

```java
class UserCommandHandler {
    @HandleCommand
    void handle(CreateUser command, Metadata metadata) {
        String userAgent = metadata.get("userAgent");
        ...
    }
}
```

### Tracking Messages

Flux Capacitor handles message dispatch asynchronously by default. When a message such as a command is published:

1. It is sent to the Flux Platform.
2. The platform logs the message and notifies all subscribed consumers.
3. Consumers stream these messages to the relevant handler methods.

### Default Consumer Behavior

By default, handlers join the **default consumer** for a given message type. For example:

```java
class MyHandler {
    @HandleCommand
    void handle(SomeCommand command) {
        ...
    }
}
```

This handler joins the default **command consumer** automatically.

### Custom Consumers with @Consumer

You can override the default behavior using the @Consumer annotation:

```java

@Consumer(name = "MyConsumer")
class MyHandler {
    @HandleCommand
    void handle(SomeCommand command) {
        ...
    }
}
```

To apply this to an entire package (and its subpackages), add a package-info.java file:

```java
@Consumer(name = "MyConsumer")
package com.example.handlers;
```

### Customizing Consumer Configuration

You can tune the behavior using additional attributes on the @Consumer annotation:

```java

@Consumer(name = "MyConsumer", threads = 2, maxFetchSize = 100)
class MyHandler {
    @HandleCommand
    void handle(SomeCommand command) {
        ...
    }
}
```

- threads = 2: Two threads per application instance will fetch commands.
- maxFetchSize = 100: Up to 100 messages fetched per request, helping apply backpressure.

Each thread runs a **tracker**. If you deploy the app multiple times, Flux automatically load-balances messages across
all available trackers.

### Default Consumer Settings

|Setting|Default Value|
|-------|-------------|
|threads|1|
|maxFetchSize|1024|

These defaults are sufficient for most scenarios. You can always override them for improved performance or control.

---

### Message Replays

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
long index = IndexUtils.indexFromTimestamp(Instant.parse("2024-01-01T00:00:00Z"));
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
```

### Dynamic Dead Letter Queue (DLQ)

The **error log is durable** and **replayable**, which means you can treat it as a **powerful, dynamic DLQ**.

Here’s how:

1. **Deploy a special consumer** that tracks the error log.
2. Use `@Trigger` to access and inspect failed messages.
3. Filter and replay failures based on time, payload type, or originating app.

### Example: Retrying Failed Commands from the Past

Let’s assume a bug caused command processing to fail in January 2024. The following setup reprocesses those failed
commands:

```java

@Consumer(name = "command-dlq", minIndex = 111677748019200000L, maxIndexExclusive = 111853279641600000L) // 2024-01-01 to 2024-02-01 
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

| Use Case                   | How the Error Log Helps                        |
|----------------------------|------------------------------------------------|
| 🛠 Fix a bug retroactively | Replay failed commands from the past           |
| 🚧 Validate new handler logic | Test it against real-world errors          |
| 🔁 Retry transient failures | Re-issue requests with retry logic            |
| 🧹 Clean up or suppress errors | Filter out known false-positives         |

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

@Value
public class ShipOrder {
    @RoutingKey
    OrderId orderId;
}
```

Or explicitly reference a nested property:

```java

@RoutingKey("customer/id")
public class OrderPlaced {
    Customer customer;
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

---

### Local handlers

Flux Capacitor supports both asynchronous and local (synchronous) message handling. **Local handlers** process messages
in the same thread that published them, bypassing the message dispatch infrastructure entirely. This typically results
in faster response times and is ideal for simple or time-sensitive use cases.

To define a local handler, annotate the handler method, class, or its enclosing package with `@LocalHandler`:

```java

@LocalHandler(logMetrics = true)
public class SomeLocalHandler {
    @HandleEvent
    void handle(ApplicationStarted event) {
        //do something
    }
}
```

> 💡 Use logMetrics = true to track performance metrics even for local handlers.

### Self-handling messages

Instead of defining message handlers externally, you can embed handler logic directly in the message payload. This is
often useful for queries or simple commands.

```java
public class GetUserProfile {
    String userId;

    @HandleQuery
    UserProfile handle() {
        //fetch the user profile and return
    }
}
```

By default, such handlers are treated as local. To process them asynchronously (i.e., as part of a consumer),
annotate the class with `@TrackSelf`:

```java

@TrackSelf
@Consumer(name = "user-management")
public class GetUserProfile {
    String userId;

    @HandleQuery
    UserProfile handle() {
        // Async handler
    }
}
```

When component-scanned (e.g., via Spring), `@TrackSelf` classes will be automatically discovered and registered.
This works even if the annotation is placed on an interface rather than the concrete class—allowing for reusable handler
patterns.

For example, a generic command handler interface can be tracked and reused:

```java

@TrackSelf
public interface UserUpdate {
    @HandleCommand
    default void handle() {
        //default behavior
    }
}
```

Implementations of this interface will then be handled asynchronously, using the configured consumer (or the default
one if unspecified).

### Response typing with Request<R>

Flux Capacitor allows you to formalize the expected return type of commands and queries by implementing the `Request<R>`
interface. This lets the framework:

- Infer the response type at runtime and during testing.
- Validate handler methods at compile-time (e.g., `@HandleQuery` must return an R).
- Simplify `queryAndWait(...)` or `sendCommand(...)` invocations.

Here’s an example for a query:

```java

@Value
public class GetUserProfile implements Request<UserProfile> {
    String userId;

    @HandleQuery
    UserProfile handle() {
        return loadProfile(userId);
    }
}
```

Now, when calling this query, the return type is automatically known:

```java
UserProfile profile = FluxCapacitor.queryAndWait(new GetUserProfile("123"));
```

When implementing Request<R>, the expected result type (R) is automatically inferred during testing and execution. This
enables type-safe tests like:

[//]: # (@formatter:off)
```java
testFixture.whenQuery(new GetUserProfile("123"))
        .expectResult(profile -> profile.getUserId().equals("123"));
```
[//]: # (@formatter:on)

If a class implements `Request<R>`, Flux will use its declared generic type (R) to check that:

- A compatible handler exists
- The handler returns the correct result type
- The correct response is expected during testing

> This pattern is highly recommended for queries, as it reduces boilerplate and improves correctness.

### Payload Validation

Flux Capacitor automatically validates incoming request payloads using [JSR 380](https://beanvalidation.org/2.0/)
(Bean Validation 2.0) annotations.

This includes support for:

- `@NotNull`, `@NotBlank`, `@Size`, etc.
- `@Valid` on nested objects
- Constraint violations in command/query/webrequest payloads

If a constraint is violated, the handler method is **never called**. Instead, a `ValidationException`,
is thrown before the handler is invoked.

```java

@Value
public class CreateUser {
    @NotBlank
    String userId;

    @NotNull
    @Valid
    UserProfile profile;
}
```

You can disable this validation entirely by calling:

[//]: # (@formatter:off)
```java
DefaultFluxCapacitor.builder().disablePayloadValidation();
```
[//]: # (@formatter:on)

Of course, it is also easy to provide your own validation if desired. For how to do that, please refer to the section
on `HandlerInterceptors`.

> 💡 **Tip**: Flux Capacitor automatically correlates errors with the triggering message (e.g.: command or event).
>
> This means you don’t need to log extra context like the message payload or user ID — that information is already
> available in the audit trail in Flux Platform. This also encourages using **clear, user-facing error messages**
> without leaking internal details.

### User and Role-Based Access Control

Flux Capacitor allows you to restrict message handling based on the authenticated user's roles. This access control
happens **before** the message reaches the handler — similar to how payload validation is enforced.

There are several annotations for declaring role requirements:

#### `@RequiresAnyRole`

Use this annotation to ensure that a handler is only invoked if the user has **at least one** of the
specified roles.

```java

@HandleCommand
@RequiresAnyRole({"admin", "editor"})
void handle(UpdateArticle command) { ...}
```

```java

@RequiresAnyRole("admin")
public record DeleteAccount(String userId) {
}
```

- **On a handler method, class, or package**: the handler is *skipped* if the user lacks a required role. Other handlers
  may still process the message.
- **On a payload class**: message handling is *blocked*, and an `UnauthorizedException` or `UnauthenticatedException` is
  thrown.

> ⚠️ **Authentication vs Authorization errors**
> - If a **user is expected but not present** (e.g. due to `@RequiresUser` or a payload requiring a role), a
    `UnauthenticatedException` is thrown.
> - If a **user is present but lacks required roles**, a `UnauthorizedException` is thrown.
>
> This applies especially when annotations are placed on the **payload class**. If the handler itself is annotated
> instead, unauthorized users will simply skip that handler (allowing delegation to others).

#### `@ForbidsAnyRole`

This annotation works the other way around — it **prevents** message handling if the user has any of the specified
roles.

```java

@ForbidsAnyRole("guest")
@HandleCommand
void handle(SensitiveOperation command) { ...}
```

#### `@RequiresUser`

Ensures that a message can only be handled if an **authenticated user** is present. If no user is found, the message is
rejected with an `UnauthenticatedException`.

This is useful for requiring login in scenarios like user account updates, sensitive commands, or personal data access.

```java

@RequiresUser
@HandleCommand
void handle(UpdateProfile command) { ...}
```

#### `@RequiresNoUser`

Allows a message to be processed even if **no authenticated user** is present — ideal for public APIs, sign-up flows, or
health checks.

```java

@RequiresNoUser
@HandleCommand
void handle(SignUpUser command) { ...}
```

---

### Role annotations support nesting and overrides

Flux evaluates these annotations hierarchically. For example:

- If `@RequiresAnyRole("admin")` is placed on a **package**, it applies to all handlers and payloads in that package by
  default.
- You can override that requirement on a specific method or class using `@RequiresAnyRole(...)`, `@ForbidsAnyRole(...)`,
  or `@RequiresNoUser`.

```java
// package-info.java
@RequiresUser
package com.myapp.handlers;
```

```java

@RequiresNoUser
@HandleCommand
void handle(PublicPing ping) { ...} // Overrides the package-level requirement
```

This allows you to apply coarse-grained defaults and override them where needed.

---

### Enum-based role annotations (meta-annotations)

For more structure, you can define **custom annotations** using enums or strongly typed roles. For example:

```java
public enum Role {
    ADMIN, EDITOR, USER
}
```

```java

@RequiresAnyRole
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresRole {
    Role[] value();
}
```

You can now annotate your handlers like this:

```java

@HandleCommand
@RequiresRole(Role.ADMIN)
void handle(DeleteAccount command) { ...}
```

Flux will interpret the enum-based annotation through the underlying `@RequiresAnyRole`.

---

### Where does user info come from?

User roles are resolved by the configured `UserProvider`, which extracts the current user from message metadata (e.g.,
authentication tokens, headers, etc.). By default, Flux Capacitor uses a pluggable SPI to register this provider.

> 💡 You can override or mock this provider in tests using the TestFixture API.

---

### Best Practices

- Use role annotations on **payload classes** to guarantee strict access checks in all environments.
- Use them on **handlers** to control fallback behavior or define role-specific processing.
- Set security defaults on **packages** or base classes, and override selectively.
- Define **custom annotations** to avoid scattering string-based role declarations.

---

> 📌 Tip: Access control is enforced transparently — there’s no need to log or repeat the user or message context.
> Flux automatically maintains correlation metadata between the original request and any errors, logs, or events that
> follow.

## Scheduling

Flux Capacitor allows scheduling messages for future delivery using the `MessageScheduler`.

Here’s an example that schedules a termination event 30 days after an account is closed:

```java
class UserLifecycleHandler {
    @HandleEvent
    void handle(AccountClosed event) {
        FluxCapacitor.schedule(
                new TerminateAccount(
                        event.getUserId()),
                "AccountClosed-" + event.getUserId(),
                Duration.ofDays(30)
        );
    }

    @HandleEvent
    void handle(AccountReopened event) {
        FluxCapacitor.cancelSchedule("AccountClosed-" + event.getUserId());
    }

    @HandleSchedule
    void handle(TerminateAccount schedule) {
        // Perform termination
    }
}
```

Alternatively, you can schedule commands using `scheduleCommand`:

```java
class UserLifecycleHandler {
    @HandleEvent
    void handle(AccountClosed event) {
        FluxCapacitor.scheduleCommand(
                new TerminateAccount(
                        event.getUserId()),
                "AccountClosed-" + event.getUserId(),
                Duration.ofDays(30));
    }

    @HandleEvent
    void handle(AccountReopened event) {
        FluxCapacitor.cancelSchedule("AccountClosed-" + event.getUserId());
    }
}
```

### Periodic scheduling

Flux Capacitor supports recurring message schedules via the `@Periodic` annotation. This makes it easy to run tasks on a
fixed interval or cron-based schedule — useful for polling, maintenance, background processing, and more.

You can apply `@Periodic` to either a `Schedule` payload or a `@HandleSchedule` method:

```java

@Value
@Periodic(delay = 5, timeUnit = TimeUnit.MINUTES)
public class RefreshData {
    String index;
}
```

This schedules RefreshData to run every 5 minutes.

Or, to use a cron expression:

```java

@Periodic(cron = "0 0 * * MON", timeZone = "Europe/Amsterdam")
@HandleSchedule
void weeklySync(PollData schedule) {
    ...
}
```

This example triggers the `weeklySync` method every Monday at 00:00 in the Amsterdam time zone.

#### Behavior and advanced options

- `@Periodic` works only for scheduled messages (see `@HandleSchedule`).
- The schedule **automatically reschedules** itself after each invocation unless canceled.
- You may:
    - Return `void` or `null` to continue with the same schedule.
    - Return a `Duration` or `Instant` to override the next deadline.
    - Return a new payload or `Schedule` to customize the next cycle.
- If an error occurs:
    - The schedule continues by default (`continueOnError = true`).
    - You may specify a fallback delay via `delayAfterError`.
    - Throw `CancelPeriodic` from the handler to stop the schedule completely.
- To prevent startup activation, use `@Periodic(autoStart = false)`.
- The schedule ID defaults to the class name, but can be customized with `scheduleId`.

Here's an example of robust polling with error fallback:

```java

@Periodic(delay = 60, timeUnit = TimeUnit.MINUTES, delayAfterError = 10)
@HandleSchedule
void pollExternalService(PollTask pollTask) {
    try {
        externalService.fetchData();
    } catch (Exception e) {
        log.warn("Polling failed, will retry in 10 minutes", e);
        throw e;
    }
}
```

In this case:

- The task runs every hour normally.
- If it fails, it retries after 10 minutes.
- It resumes the original schedule if the next invocation succeeds.

### Handling Web Requests

Flux Capacitor supports first-class **WebRequest handling** via the `@HandleWeb` annotation and its HTTP-specific
variants such as `@HandleGet`, `@HandlePost`, `@HandleDelete`, etc.

Instead of exposing a public HTTP server per application, Flux uses a central Web Gateway that **proxies all external
HTTP(S) and WebSocket traffic into the platform as `WebRequest` messages**. These messages are:

- **Logged** for traceability and auditing
- **Routed to client applications** using the same handler system as for commands, events, and queries
- **Handled by consumer applications** which return a `WebResponse`

#### Why This Design?

This architecture enables several key benefits:

- ✅ **Zero exposure**: client apps do not require a public-facing HTTP server and are thus *invisible* to attackers
- ✅ **Back-pressure support**: applications control load by polling their own messages
- ✅ **Audit-friendly**: every incoming request is automatically logged and correlated to its response
- ✅ **Multiple consumers possible**: multiple handlers can react to a WebRequest, though typically only one produces the
  response (others use `passive = true`)

#### Example

```java

@HandleGet("/users")
public List<User> listUsers() {
    return userService.getAllUsers();
}
```

This will match incoming GET requests to `/users` and return a list of users. The response is published back as a
`WebResponse`.

You can use the general `@HandleWeb` if you want to match multiple methods or define a custom method:

```java

@HandleWeb(value = "/users/*", method = {"GET", "DELETE"})
public Object handleUserRequest(WebRequest request) {
    return switch (request.getMethod()) {
        case "GET" -> userService.get(request.getPath());
        case "DELETE" -> userService.delete(request.getPath());
        default -> throw new IllegalArgumentException("Unsupported method");
    };
}
```

#### Suppressing a Response

If you want to listen to a WebRequest without generating a response (e.g. for auditing or monitoring), use
`passive = true`:

```java

@HandlePost(value = "/log", passive = true)
public void log(WebRequest request) {
    logService.store(request);
}
```

#### Dynamic Path Parameters

Use the `@PathParam` annotation to extract dynamic segments from the URI path into handler method parameters:

```java

@HandleGet("/users/{id}")
public User getUser(@PathParam String id) {
    return userService.get(id);
}
```

If the `value` is left empty, the framework will use the parameter name (`id` in this case).

#### URI Prefixing with `@Path`

You can use the `@Path` annotation at the package, class, or method level to declare a URI prefix that is prepended to
any path in the handler annotations:

```java

@Path("/users")
public class UserController {

    @HandleGet("/{id}")
    public User getUser(@PathParam String id) {
        return userService.get(id);
    }
}
```

This defines a route at `/users/{id}`. The prefixing is **hierarchical**:

- method-level `@Path` overrides class-level
- class-level overrides package-level

#### Other Parameter Annotations

In addition to `@PathParam`, you can extract other values from the request using:

- `@QueryParam` – extract query string values
- `@HeaderParam` – extract HTTP headers
- `@CookieParam` – extract cookie values
- `@FormParam` – extract form-encoded values (for POST/PUT)

Each of these annotations supports the same rules:

- If no name is given, the method parameter name is used
- Values are automatically converted to the target parameter type

### Handling WebSocket Messages

Flux Capacitor provides first-class support for **WebSocket communication**, enabling stateful or stateless message
handling using the same annotation-based model as other requests.

WebSocket requests are published to the **WebRequest** log after being reverse-forwarded from the Flux platform, and can
be consumed and responded to like any other request type.

---

### Two Styles of WebSocket Handling

#### 1. **Stateless Handlers** (Singleton Style)

Use annotations like `@HandleSocketOpen`, `@HandleSocketMessage`, and `@HandleSocketClose` directly on singleton handler
classes:

```java

@HandleSocketOpen("/chat")
public String onOpen() {
    return "Welcome!";
}

@HandleSocketMessage("/chat")
public String onMessage(String incoming) {
    return "Echo: " + incoming;
}

@HandleSocketClose("/chat")
public void onClose(SocketSession session) {
    System.out.println("Socket closed: " + session.sessionId());
}
```

Responses can be returned directly from `@HandleSocketMessage` and `@HandleSocketOpen` methods. For more control, you
can inject the `SocketSession` parameter and send messages manually.

Other available annotations:

- `@HandleSocketPong` — handle pong responses
- `@HandleSocketHandshake` — override the default handshake logic

#### 2. **Stateful Sessions with `@SocketEndpoint`**

If you need to maintain per-session state, use `@SocketEndpoint`. Each WebSocket session will instantiate a fresh
handler object:

```java

@SocketEndpoint
@Path("/chat")
public class ChatSession {

    private final List<String> messages = new ArrayList<>();

    @HandleSocketOpen
    public String onOpen() {
        return "Connected!";
    }

    @HandleSocketMessage
    public void onMessage(String text, SocketSession session) {
        messages.add(text);
        session.sendMessage("Stored message: " + text);
    }

    @HandleSocketClose
    public void onClose() {
        System.out.println("Messages in this session: " + messages.size());
    }
}
```

Stateful sessions are useful for flows involving authentication, message accumulation, or temporal context (e.g. cursor,
buffer, sequence).

> ✅ `@SocketEndpoint` handlers are prototype-scoped, meaning they're constructed once per session.

---

### Automatic Ping-Pong & Keep-Alive

When using `@SocketEndpoint`, Flux Capacitor automatically manages **keep-alive pings**:

- Pings are sent at regular intervals (default: every 60s)
- If a `pong` is not received within a timeout, the session is closed
- You can customize this behavior via the `aliveCheck` attribute on `@SocketEndpoint`

```java

@SocketEndpoint(aliveCheck = @SocketEndpoint.AliveCheck(pingDelay = 30, pingTimeout = 15))
public class MySession { ...
}
```

---

### Summary

| Annotation                 | Description                                         |
|----------------------------|-----------------------------------------------------|
| `@HandleSocketOpen`        | Handles WebSocket connection open                  |
| `@HandleSocketMessage`     | Handles incoming text or binary WebSocket messages |
| `@HandleSocketPong`        | Handles pong responses (usually for health checks) |
| `@HandleSocketClose`       | Handles WebSocket session closing                  |
| `@HandleSocketHandshake`   | Allows customizing the handshake phase             |
| `@SocketEndpoint`          | Declares a per-session WebSocket handler class     |
| `SocketSession` (injected) | Controls sending messages, pinging, and closing    |

Flux Capacitor makes WebSocket communication secure, observable, and composable—integrated seamlessly into your
distributed, event-driven architecture.

## Testing your handlers

Flux Capacitor comes with a flexible, expressive testing framework based on the given-when-then pattern. This enables
writing behavioral tests for your handlers without needing to mock the infrastructure.

Here’s a basic example:

```java
TestFixture testFixture = TestFixture.create(new UserEventHandler());

@Test
void newUserGetsWelcomeEmail() {
    testFixture.whenEvent(new UserCreated(myUserProfile))
            .expectCommands(new SendWelcomeEmail(myUserProfile));
}
```

This test ensures that when a `UserCreated` event occurs, a `SendWelcomeEmail` command is issued by the handler.

### Testing complete workflows

You can test full workflows across multiple handlers:

```java
TestFixture fixture = TestFixture.create(new UserCommandHandler(), new UserEventHandler());

@Test
void creatingUserTriggersEmail() {
    fixture.whenCommand(new CreateUser(userProfile))
            .expectCommands(new SendWelcomeEmail(userProfile));
}
```

Use `expectOnlyCommands()` to assert that **only** the expected command was issued:

[//]: # (@formatter:off)
```java
fixture.whenCommand(new CreateUser(userProfile))
       .expectOnlyCommands(new SendWelcomeEmail(userProfile));
```
[//]: # (@formatter:on)

You can also match by class, predicate, or Hamcrest matcher:

[//]: # (@formatter:off)
```java
fixture.whenCommand(new CreateUser(userProfile))
       .expectCommands(SendWelcomeEmail.class, isA(AddUserToOrganization.class));
```
[//]: # (@formatter:on)

### Chained expectations

Multiple expectations can be chained to test the full sequence of events and commands:

[//]: # (@formatter:off)
```java
fixture.whenCommand(new CreateUser(userProfile))
       .expectCommands(new SendWelcomeEmail(userProfile))
       .expectEvents(new UserStatsUpdated(...));
```
[//]: # (@formatter:on)

You can also chain multiple **inputs** using `.andThen()` to simulate a sequence of events, commands, or queries:

[//]: # (@formatter:off)
```java
fixture.whenCommand(new CreateUser(userProfile))
       .expectCommands(new SendWelcomeEmail(userProfile))
       .andThen()
       .whenQuery(new GetUser(userId))
       .expectResult(userProfile);
```
[//]: # (@formatter:on)

This example first triggers a `CreateUser` command, expects a `UserCreated` event, and then issues a `GetUser` query,
asserting that it returns the expected result.

### Using givenXxx() for preconditions

Use `givenCommands`, `givenEvents`, etc., to simulate preconditions:

[//]: # (@formatter:off)
```java
fixture.givenCommands(new CreateUser(userProfile), new ResetPassword(...))
       .whenCommand(new UpdatePassword(...))
       .expectEvents(UpdatePassword.class);
```
[//]: # (@formatter:on)

### JSON-based input and output

To use serialized JSON for inputs or expected outputs, provide a `.json` path:

[//]: # (@formatter:off)
```java
fixture.givenCommands("create-user.json")
       .whenQuery(new GetUser(userId))
       .expectResult("user-profile.json");
```
[//]: # (@formatter:on)

The file is resolved relative to the test class’s package in the classpath (e.g., `/org/example/create-user.json`),
unless you provide an absolute path (starting with `/`).

Make sure your JSON includes the @class attribute to enable deserialization:

```json
{
  "@class": "com.example.UserProfile",
  "userId": "3290328",
  "email": "foo.bar@example.com"
}
```

### Adding or asserting metadata

Wrap your payload in a Message to add or assert metadata:

```java

@Test
void newAdminGetsAdditionalEmail() {
    testFixture.whenCommand(new Message(new CreateUser(...),Metadata.of("roles", Arrays.asList("Customer", "Admin"))))
        .expectCommands(new SendWelcomeEmail(...),new SendAdminEmail(...));
}
```

### Result and exception assertions

You can assert the result returned by a command or query:

[//]: # (@formatter:off)
```java
fixture.givenCommands(new CreateUser(userProfile))
       .whenQuery(new GetUser(userId))
       .expectResult(userProfile);
```
[//]: # (@formatter:on)

To assert an exception:

[//]: # (@formatter:off)
```java
fixture.givenCommands(new CreateUser(userProfile))
        .whenCommand(new CreateUser(userProfile))
        .expectExceptionalResult(IllegalCommandException.class);
```
[//]: # (@formatter:on)

### User-aware tests

You can also simulate a command from a specific user:

[//]: # (@formatter:off)
```java
var user = new MyUser("pete");

fixture.whenCommandByUser(user, "confirm-user.json")
       .expectExceptionalResult(UnauthorizedException.class);
```
[//]: # (@formatter:on)

You can also pass a user ID string directly instead of a full User object. The test fixture will resolve it using the
configured `UserProvider` (by default loaded via Java's `ServiceLoader`):

[//]: # (@formatter:off)
```java
fixture
    .givenCommands("create-user-pete.json")
    .whenCommandByUser("pete", "confirm-user.json")
    .expectExceptionalResult(UnauthorizedException.class);
```
[//]: # (@formatter:on)

In this example, the string "pete" is resolved to a `User` instance using the active `UserProvider`, and the test
asserts
that the confirmation is unauthorized.

### Verifying side effects

Use `expectThat()` or `expectTrue()` to assert custom logic or verify interactions (e.g., using Mockito):

[//]: # (@formatter:off)
```java
fixture.whenCommand(new CreateUser(userProfile))
       .expectThat(fc -> Mockito.verify(emailService).sendEmail(...));
```
[//]: # (@formatter:on)

### Triggering side effects manually

Use `whenExecuting()` to test code outside of message dispatching, like HTTP calls:

[//]: # (@formatter:off)
```java
fixture.whenExecuting(fc -> httpClient.put("/user", userProfile))
       .expectEvents(new UserCreated(...));
```
[//]: # (@formatter:on)

### Asynchronous tests

By default, `TestFixture.create(...)` creates synchronous fixtures where handlers are executed locally in the same
thread
that publishes the message. This provides fast, deterministic behavior ideal for most unit tests.

However, in production your handlers are typically dispatched asynchronously by consumers running on separate threads.
To better simulate this behavior in tests — especially when testing event-driven flows or stateful consumers — you can
use an asynchronous fixture instead.

```java
TestFixture fixture = TestFixture.createAsync(new MyHandler(), MyStatefulHandler.class);
```

This ensures that:

- Handlers are tracked via the same consumer infrastructure used in production.
- Behavior involving asynchronous dispatch, retries, or stateful models is tested realistically.
- Eventual consistency is respected (e.g., expect...() calls will wait for outcomes to materialize).

> **Note:** Handlers annotated with `@LocalHandler` are executed synchronously, even in async fixtures, just as they
> would
> in production. Handlers annotated with `@TrackSelf`, `@Stateful`, or `@SocketEndpoint` — also behave as they would in
> a production runtime: they are tracked and dispatched asynchronously when registered by class.

### Using test fixtures in Spring

When using Spring, simply inject the test fixture via `FluxCapacitorTestConfig`:

```java

@SpringBootTest(classes = {App.class, FluxCapacitorTestConfig.class})
class AsyncAppTest {

    @Autowired
    TestFixture fixture;

    @Test
    void testSomething() {
        fixture.whenCommand("commands/my-command.json")
                .expectEvents("events/expected-event.json");
    }
}
```

By default, this will inject an **async** test fixture. You can override this by setting the property:

```properties
fluxcapacitor.test.sync=true
```

Or selectively enable sync mode via per-test configuration using:

```java

@TestPropertySource(properties = "fluxcapacitor.test.sync=true")
@SpringBootTest(classes = {App.class, FluxCapacitorTestConfig.class})
class SyncAppTest {

    @Autowired
    TestFixture fixture;
  
  ...
}
```

### Testing schedules

Flux Capacitor’s scheduling engine makes it easy to test time-based workflows. Scheduled messages are handled just
like any other message, except they are delayed until their due time.

Use the `TestFixture` to simulate the passage of time and trigger scheduled actions. Here’s a typical example:

```java
TestFixture testFixture = TestFixture.create(new UserCommandHandler(), new UserLifecycleHandler());

@Test
void accountIsTerminatedAfterClosing() {
    testFixture
            .givenCommands(new CreateUser(myUserProfile), new CloseAccount(userId))
            .whenTimeElapses(Duration.ofDays(30))
            .expectEvents(new AccountTerminated(userId));
}
```

In this test:

- We schedule the AccountTerminated message as a result of the CloseAccount command.
- Then we simulate that 30 days have passed using whenTimeElapses(...).
- Finally, we verify that the expected AccountTerminated event was published.

You can also test cancellation logic:

```java

@Test
void accountReopeningCancelsTermination() {
    testFixture
            .givenCommands(new CreateUser(myUserProfile), new CloseAccount(userId), new ReopenAccount(userId))
            .whenTimeElapses(Duration.ofDays(30))
            .expectNoEventsLike(AccountTerminated.class);
}
```

If needed, you can advance time to an absolute timestamp using:

[//]: # (@formatter:off)
```java
fixture.whenTimeAdvancesTo(Instant.parse("2050-12-31T00:00:00Z"));
```
[//]: # (@formatter:on)

This is especially useful for workflows tied to specific deadlines or calendar dates.

## Domain Modeling

Flux Capacitor allows you to model the state of your domain using entities that evolve over time by applying updates.
These entities — such as users, orders, etc. — maintain state and enforce invariants through controlled updates,
typically driven by commands.

### Defining the Aggregate Entity

To define a stateful domain object, annotate it with `@Aggregate`:

```java

@Aggregate
@Value
@Builder(toBuilder = true)
public class User {
    @EntityId
    UserId userId;
    UserProfile profile;
    boolean accountClosed;
}
```

This `User` class models an aggregate with state such as `profile` and `accountClosed`. Each entity may contain a field
annotated with `@EntityId` that acts as a unique identifier. For aggregates, this is optional — the aggregate itself is
typically loaded using `FluxCapacitor.loadAggregate(id)`.

An **aggregate** is a specialized root entity that serves as an entry point into a domain model. It may contain nested
child entities (modeled via `@Member`), but represents a single unit of consistency.

---

### Applying Updates with @Apply and @AssertLegal

Entities evolve in response to **updates** — typically the payload of a command. Updates define what change should
happen and contain the logic to validate and apply those changes.

Here's an example using two command classes: one to create a user, and another to update the profile.

```java

@Value
public class CreateUser {
    UserId userId;
    UserProfile profile;

    @AssertLegal
    void assertNotExists(User current) {
        throw new IllegalCommandException("User already exists");
    }

    @Apply
    User apply() {
        return new User(userId, profile, false);
    }
}
```

This command creates a new `User` entity after asserting that no user with the given ID exists.

> **Note**: Handler method parameters (like `User current`) are only injected if non-null by default. Use `@Nullable` if
> you need to allow null.

```java

@Value
public class UpdateProfile {
    UserId userId;
    UserProfile profile;

    @AssertLegal
    void assertExists(@Nullable User current) {
        if (current == null) {
            throw new IllegalCommandException("User not found");
        }
    }

    @AssertLegal
    void assertAccountNotClosed(User current) {
        if (current.isAccountClosed()) {
            throw new IllegalCommandException("Account is closed");
        }
    }

    @Apply
    User apply(User current) {
        return current.toBuilder().profile(profile).build();
    }
}
```

This update first checks whether the user exists and if their account is still open before applying the update.
Returning a new `User` object here reflects the recommended **immutable style**, but mutable updates are supported too.

> **Note**: Since parameters like `User current` are only injected when non-null, you can safely omit null checks
> unless you annotate with `@Nullable`.

---

### Why Keep Logic in the Updates?

While it’s possible to implement domain logic inside entities, this is **generally discouraged**. Instead, it is best
practice to define business logic directly inside **command payloads** — the updates themselves.

This update-driven approach has several advantages:

- **Behavior stays with the update** – each update class (e.g. `CreateUser`, `UpdateProfile`) encapsulates its own
  validation and transformation logic.
- **Entities stay focused** – entities remain concise, responsible only for maintaining state and enforcing invariants.
- **Easy feature cleanup** – removing an update class cleanly disables that feature.
- **Traceable domain behavior** – it’s clear what each update does and how it affects the system.

---

### Alternative: Logic in the Entity

Although possible, modeling behavior inside the aggregate can quickly become unmanageable. Here's a glimpse of what that
looks like:

```java

@Aggregate
@Value
@Builder(toBuilder = true)
public class User {
    @EntityId
    UserId userId;
    UserProfile profile;
    boolean accountClosed;

    @AssertLegal
    static void assertNotExists(CreateUser update, @Nullable User user) {
        if (user != null) {
            throw new IllegalCommandException("User already exists");
        }
    }

    @Apply
    static User create(CreateUser update) {
        return new User(update.getUserId(), update.getProfile(), false);
    }

    @AssertLegal
    static void assertExists(UpdateProfile update, @Nullable User user) {
        if (user == null) {
            throw new IllegalCommandException("User does not exist");
        }
    }

    @AssertLegal
    void assertAccountNotClosed(UpdateProfile update) {
        if (accountClosed) {
            throw new IllegalCommandException("Account is closed");
        }
    }

    @Apply
    User update(UpdateProfile update) {
        return toBuilder().profile(update.getProfile()).build();
    }
}
```

In this model, the `User` aggregate handles all validation and transformation logic. Over time, this centralization
leads to bloat and tight coupling — especially in larger systems with many features.

---

### Mixing strategies

Flux Capacitor allows **mixed approaches**. You can define:

- `@AssertLegal` methods on the command payload
- `@Apply` methods inside the entity
- or vice versa

Just keep in mind: logic that lives in updates is **easier to test, extend, and remove**.

## Applying Updates in Handlers

To change the state of an entity, use `FluxCapacitor.loadAggregate(...)` to retrieve the aggregate and apply updates to
it.

Here's a basic example of a command handler applying a `CreateUser` update:

```java
public class UserCommandHandler {
    @HandleCommand
    void handle(CreateUser command) {
        FluxCapacitor.loadAggregate(command.getUserId(), User.class).assertAndApply(command);
    }
}
```

This loads the `User` entity by ID and applies the `CreateUser` command. Internally, Flux Capacitor will:

1. **Rehydrate** the entity using stored events or snapshots
2. **Run all `@AssertLegal` methods** to verify preconditions
3. **Call the `@Apply` method** to produce a new entity state
4. **Persist the event** in the aggregate event log (if event sourcing is active)
5. **Publish a domain event** (using the applied payload, unless overridden)

This example used `assertAndApply()`, which combines two steps:

[//]: # (@formatter:off)
```java
.aggregate(...)
  .assertLegal(update)
  .apply(update);
```
[//]: # (@formatter:on)

This style is recommended if you want to ensure validations happen before the entity changes state.

## Nested Entities and Members

Flux Capacitor allows aggregates to contain nested entities — for example, users with authorizations or orders with line
items. These nested entities can be added, updated, or removed using the same `@Apply` pattern used for root aggregates.

To define a nested structure, annotate the collection or field with `@Member`:

```java

@Aggregate
@Value
@Builder(toBuilder = true)
public class User {
    @EntityId
    UserId userId;
    UserProfile profile;
    boolean accountClosed;

    @Member
    List<Authorization> authorizations;
}
```

Child entities must define their own `@EntityId`:

```java

@Value
public class Authorization {
    @EntityId
    AuthorizationId authorizationId;
    Grant grant;
}
```

### Adding a Child Entity

To add a nested entity like `Authorization`, simply return a new instance from the `@Apply` method:

```java

@Value
public class AuthorizeUser {
    AuthorizationId authorizationId;
    Grant grant;

    @Apply
    Authorization apply() {
        return new Authorization(authorizationId, grant);
    }
}
```

The `User` aggregate is automatically updated to include this new child entity.

### Removing a Child Entity

To remove a nested entity, return `null` from the `@Apply` method:

```java

@Value
public class RevokeAuthorization {
    AuthorizationId authorizationId;

    @AssertLegal
    void assertExists(@Nullable Authorization authorization) {
        if (authorization == null) {
            throw new IllegalCommandException("Authorization not found");
        }
    }

    @Apply
    Authorization apply(Authorization authorization) {
        return null;
    }
}
```

Flux will automatically prune the child entity with the given `authorizationId`.

### Routing Behavior

Flux automatically routes child-targeted updates like `AuthorizeUser` and `RevokeAuthorization` to the correct nested
entity using the `@EntityId`. You don’t need to write custom matching logic — the routing works transparently as long
as:

- The root aggregate is loaded (e.g. using `loadAggregate(userId, User.class)`), and
- The update contains enough identifying information to locate the nested entity

### Summary

This model leads to extremely clean domain logic:

- No need to manipulate collections in the aggregate
- No need for boilerplate logic to find, update, or remove children
- Nested updates stay localized to the child entity itself

## Model Persistence

Flux Capacitor supports multiple strategies for storing and reloading aggregates:

- **Event sourcing**: state is derived by replaying a stream of applied updates (events)
- **Document storage**: the full aggregate is stored as a document
- **In-memory only**: ephemeral state, not persisted across messages

By default, `@Aggregate` uses **event sourcing**, but you can configure each aggregate individually.

---

### Event Sourcing

Flux Capacitor uses **event sourcing** by default for all `@Aggregate` types (`eventSourced = true` by default).

Each time an update is applied:

1. The aggregate is **rehydrated** from its event history
2. The update is validated via `@AssertLegal` methods
3. The new state is computed via an `@Apply` method
4. The update is appended to the **event store**
5. The update is published to the **event log**
6. The aggregate is cached or indexed (if enabled)

You can customize event persistence behavior with:

- `eventPublication`: prevent events when nothing has changed
- `publicationStrategy`: store-only vs publish-and-store
- `snapshotPeriod`: snapshot every N updates
- `ignoreUnknownEvents`: handle versioned aggregates gracefully

Here’s a simple example:

```java

@Aggregate(snapshotPeriod = 1000)
@Value
public class User {
    @EntityId
    UserId userId;
    UserProfile profile;

    @Apply
    User apply(UpdateProfile update) {
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

@Aggregate(eventSourced = false, searchable = true)
@Value
public class Country {
    @EntityId
    String countryCode;
    String name;
}
```

This stores the entire entity as a document. The entity can still use `@Apply` and `@AssertLegal`, and changes are
persisted to the document store.

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
@Value
public class Order { ...
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

> ✅ When loading an aggregate inside an event handler, Flux ensures that the returned entity is always up-to-date.  
> If the event being handled is part of that aggregate, the aggregate is automatically rehydrated *up to and
including*  
> the current event. Flux will wait (if needed) until the aggregate has caught up to that point, ensuring consistency  
> and preventing stale reads — even during concurrent or out-of-order processing.

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
                    "Unusual balance change on account %s".formatted(current.getAccountId())));
        }
    }

    boolean hasSuspiciousDelta(BankAccount previous, BankAccount current) {
        if (previous == null || current == null) {
            return false;
        }
        BigDecimal delta = current.getBalance().subtract(previous.getBalance()).abs();
        return delta.compareTo(BigDecimal.valueOf(10_000)) > 0;
    }
}
```

In this example:

- The aggregate (`BankAccount`) is automatically loaded in-sync with the current event being handled.
- The handler has access to both the **current state** and the **previous state** of the entity.
- It uses this to decide whether a significant balance change has occurred.
- No external store or manual query is needed — this is pure, consistent, event-sourced state.

## Stateful Handlers

While aggregates represent domain entities, Flux also supports long-lived **stateful handlers** for modeling workflows,
external interactions, or background processes that span multiple messages.

To declare a stateful handler, annotate a class with `@Stateful`:

```java

@Value
@Stateful
public class PaymentProcess {
    @EntityId
    String id;
    @Association
    String pspReference;
    PaymentStatus status;

    @HandleEvent
    static PaymentProcess on(PaymentInitiated event) {
        String pspRef = FluxCapacitor.sendCommandAndWait(new ExecutePayment(...));
        return new PaymentProcess(event.getPaymentId(), pspRef, PaymentStatus.PENDING);
    }

    @HandleEvent
    PaymentProcess on(PaymentConfirmed event) {
        return withStatus(PaymentStatus.CONFIRMED);
    }
}
```

### Key Properties

- `@Stateful` classes persist their state using Flux’s document store (or a custom `HandlerRepository`)
- They are automatically invoked when messages match their associations (`@Association` fields or methods)
- Matching is dynamic and supports multiple handlers per message
- Handlers are immutable by convention — they are updated by returning a new version of themselves
- Returning `null` deletes the handler (useful for terminating flows)

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

## Document Indexing and Search

Flux Capacitor provides a powerful and flexible document store that lets you persist and query models using full-text
search, filters, and time-based constraints.

This system is especially useful for:

- Querying across entities (e.g., active users, recent payments)
- Supporting projections for read APIs or dashboards
- Tracking workflows, external states, or business processes
- Replacing the need for a traditional read model database

---

### Manual Indexing

You can index any object manually using:

```java
FluxCapacitor.index(myObject);
```

This stores `myObject` in the document store so it can be queried later via `FluxCapacitor.search(...)`.

- If the object is annotated with `@Searchable`, any declared `collection`, `timestampPath`, or `endPath` will be used.
- If a field is annotated with `@EntityId`, it becomes the document ID. Otherwise, a random ID is generated.
- Timestamps can be inferred from annotated paths or passed explicitly.

You can also specify the collection in which the object should be stored directly:

```java
FluxCapacitor.index(myObject, "customCollection");
```

---

### Searchable Domain Models

Many models in Flux (e.g. aggregates or stateful handlers) are automatically indexable:

- `@Aggregate(searchable = true)`
- `@Stateful` (implicitly `@Searchable`)
- Directly annotate any POJO with `@Searchable`

This enables automatic indexing after updates or message handling, without needing to call `FluxCapacitor.index(...)`
manually.

```java

@Aggregate(searchable = true)
@Value
public class User {
    @EntityId
    UserId userId;
    UserProfile profile;
    boolean accountClosed;
}
```

By default, the collection name is derived from the class’s **simple name** (User → `"User"`),
unless explicitly overridden via an annotation like `@Aggregate`, `@Stateful` or `@Searchable` or in the search/index
call:

```java
@Aggregate(searchable = true, collection = "users", timestampPath = "profile/createdAt")
```

---

### Querying Indexed Documents

Use the fluent `search(...)` API:

```java
List<User> admins = FluxCapacitor.search("users")
        .match("admin", "profile/role")
        .inLast(Duration.ofDays(30))
        .sortBy("profile/lastLogin", true)
        .fetch(100);
```

You can also query by class:

```java
List<User> users = FluxCapacitor.search(User.class)
        .match("Netherlands", "profile.country")
        .fetchAll();
```

> **Note:** You can choose to split path segments using either a dot (`.`) or a slash (`/`).  
> For example, `profile.name` and `profile/name` are treated identically.  
> This flexibility can be useful when working with tools or serializers that prefer one style over the other.

---

### Common Filtering Constraints

Flux supports a rich set of constraints:

- `match(value, path)` – field match
- `matchFacet(name, value)` – match field with `@Facet`
- `query("text", paths...)` – full-text search
- `between(min, max, path)` – numeric or time ranges
- `since(...)`, `before(...)`, `inLast(...)` – temporal filters
- `anyExist(...)` – match if *any* of the fields are present
- Logical operations: `not(...)`, `all(...)`, `any(...)`

Example:

[//]: # (@formatter:off)
```java
FluxCapacitor.search("payments")
    .match("FAILED","status")
    .inLast(Duration.ofHours(1))
    .fetchAll();
```
[//]: # (@formatter:on)

---

### Matching Facet Fields

If you're filtering on a field that is marked with `@Facet`, it's better to use:

[//]: # (@formatter:off)
```java
.matchFacet("status", "archived")
```
[//]: # (@formatter:on)

instead of:

[//]: # (@formatter:off)
```java
.match("archived", "status")
```
[//]: # (@formatter:on)

While both achieve the same result, `matchFacet(...)` is generally **faster and more efficient**.  
That's because facet values are indexed and matched entirely at the data store level,  
whereas `.match(...)` may involve resolving the path in memory and combining constraints manually.

> **Tip:** Use `@Facet` on frequently-filtered fields (e.g. `status`, `type`, `category`) to take full advantage
> of this optimization.

---

### Facet Statistics

When a field is annotated with `@Facet`, you can also retrieve **facet statistics** — e.g., how many documents exist
per value of a given field. This is useful for building **filters with counts**, such as product categories, user roles,
or status indicators.

#### Example: Product Breakdown by Category and Brand

Suppose you have the following model:

```java

@Searchable
@Value
public class Product {
    @Facet
    String category;
    @Facet
    String brand;
    String name;
    BigDecimal price;
}
```

You can retrieve facet stats like this:

[//]: # (@formatter:off)
```java
List<FacetStats> stats = FluxCapacitor.search(Product.class)
        .lookAhead("wireless")
        .facetStats();
```
[//]: # (@formatter:on)

This gives you document counts per facet value:

[//]: # (@formatter:off)
```json
[
  { "name": "category", "value": "headphones", "count": 81 },
  { "name": "brand", "value": "Acme", "count": 45 },
  { "name": "brand", "value": "NoName", "count": 10 }
]
```
[//]: # (@formatter:on)

> **Tip:** Use `matchFacet("category", "headphones")` to filter efficiently by facet value. This is generally
> faster than `match(...)`.

Each `FacetStats` object will contain:

- the facet name (e.g., `category`)
- the distinct values (e.g., `"electronics"`, `"clothing"`)
- the number of documents per value

---

### Customizing Returned Fields

You can include or exclude specific fields:

[//]: # (@formatter:off)
```java
FluxCapacitor.search("users")
    .includeOnly("userId","profile.email")
    .exclude("profile.password")
    .fetch(50);
```
[//]: # (@formatter:on)

---

### Streaming Results

Flux supports efficient streaming of large result sets:

[//]: # (@formatter:off)
```java
FluxCapacitor.search("auditTrail")
    .inLast(Duration.ofDays(7))
    .stream().forEach(auditEvent -> process(auditEvent));
```
[//]: # (@formatter:on)

---

### Deleting Documents

To remove documents from the index:

[//]: # (@formatter:off)
```java
FluxCapacitor.search("expiredTokens")
    .before(Instant.now())
    .delete();
```
[//]: # (@formatter:on)

---

### Summary

- Use `FluxCapacitor.index(...)` to manually index documents.
- Use `@Searchable` to configure the collection name or time range for an object.
- Use `@Aggregate(searchable = true)` or `@Stateful` for automatic indexing.
- Use `FluxCapacitor.search(...)` to query, stream, sort, and aggregate your documents.

## Serialization, Upcasting, and Downcasting

Flux Capacitor uses a `Serializer` to convert message payloads, snapshots, key-value entries, and other stored data into
a binary format (typically `byte[]`). By default, the client uses a Jackson-based implementation that serializes objects
to JSON.

The serializer is fully pluggable, and you can supply your own by implementing or extending `AbstractSerializer`.

---

### Revisions

To track changes in your data model, annotate your class with `@Revision`. When deserializing, Flux will use this
revision number to determine whether any transformation is required.

```java

@Revision(1)
public class CreateUser {
    String userId; // renamed from `id`
}
```

---

### Upcasting

Upcasting transforms a serialized object from an older revision to a newer one.

```java
class CreateUserUpcaster {

    @Upcast(type = "com.example.CreateUser", revision = 0)
    ObjectNode upcastV0toV1(ObjectNode json) {
        json.set("userId", json.remove("id"));
        return json;
    }
}
```

This method is applied before deserialization. The object is transformed as needed so your code always receives the
current version.

To also modify the **type name**, return a `Data<ObjectNode>`:

```java

@Upcast(type = "com.example.CreateUser", revision = 0)
Data<ObjectNode> renameType(Data<ObjectNode> data) {
    return data.withType("com.example.RegisterUser");
}
```

You can even change a message’s `metadata` during upcasting:

```java

@Upcast(type = "com.example.CreateUser", revision = 0)
SerializedMessage changeMetadata(SerializedMessage message) {
    return message.withMetadata(
            message.getMetadata().add("timestamp",
                                      Instant.ofEpochMilli(message.getTimestamp()).toString())
    );
}
```

This can be useful for retrofitting missing fields, adding tracing info, or migrating older messages to include
required metadata keys.

---

### Dropping or Splitting Messages

Upcasters can also **drop** a message or **split** it into multiple new ones:

```java

@Upcast(type = "com.example.CreateUser", revision = 0)
void dropIfDeprecated(ObjectNode json) {
    // returning void removes this message from the stream
}

@Upcast(type = "com.example.CreateUser", revision = 0)
Stream<Data<ObjectNode>> split(Data<ObjectNode> data) {
    return Stream.of(data, new Data<>(...));
}
```

This works for **any** stored data — not just events, but also snapshots, key-value entries, and documents.

---

### Downcasting

Downcasting does the reverse: it converts a newer object into an older format. This is useful for emitting *
*legacy-compatible** data or supporting **external systems**.

```java
class CreateUserDowncaster {

    @Downcast(type = "com.example.CreateUser", revision = 1)
    ObjectNode downcastV1toV0(ObjectNode json) {
        json.set("id", json.remove("userId"));
        return json;
    }
}
```

---

### Registration

When using Spring, any bean containing `@Upcast` or `@Downcast` methods is **automatically registered** with the
serializer.

Outside of Spring, register them manually:

```java
serializer.registerCasters(new CreateUserUpcaster(), new

CreateUserDowncaster());
```

---

### How It Works

- On **deserialization**:
    - Flux detects the revision of the stored object
    - Applies all applicable `@Upcast` methods (in order)
    - Then deserializes into the latest version

- On **serialization**:
    - Flux stores the latest type and revision
    - If needed, a `@Downcast` can adapt it for external use

All casting occurs **in your application**, not in the Flux platform. Stored messages remain unchanged.

---

### Best Practices

- Use `@Revision` to version any payloads that are stored or transmitted
- Use `ObjectNode` for simple structural changes, or `Data<ObjectNode>` to modify metadata
- Chain upcasters one revision at a time (`v0 → v1`, `v1 → v2`, etc.)
- Ensure upcasters are **side-effect free** and **deterministic**

> **Note:** Upcasting is essential when using event sourcing or durable message storage — these messages may live for
> years.

## Filtering Object Content

Flux Capacitor provides a flexible way to **redact or tailor object content per user** using the `@FilterContent`
annotation.

This enables domain models or documents to define exactly what is visible to different users, based on roles, ownership,
or context.

### Basic Example

```java

@FilterContent
public Order filter(User user) {
    return user.hasRole(Role.admin) ? this : new Order(maskSensitiveFieldsOnly());
}
```

To invoke filtering:

```java
Order filtered = FluxCapacitor.filterContent(order, currentUser);
```

### Recursive Filtering

Filtering applies **recursively** to fields and nested objects. If a nested item is a list, map, or complex structure,
it will also be filtered using its own `@FilterContent` method if present.

If a nested object returns `null` from filtering:

- It is **removed from a list**
- It is **excluded from a map**

### Root Context Injection

Filtering methods can optionally accept both:

- The current `User`
- The **root object** being filtered

This is useful for making decisions based on global context.

```java

@FilterContent
public LineItem filter(User user, Order root) {
    return root.isOwner(user) ? this : null;
}
```

### Key Behaviors

- `@FilterContent` applies only when called via `FluxCapacitor.filterContent(...)` or `Serializer.filterContent(...)`
- **It is not automatic** — for performance reasons, content filtering is not applied implicitly (e.g. during search or
  document deserialization)
- If no method is annotated with `@FilterContent`, the object is returned unmodified

## Parameter resolvers

The parameters of annotated handler methods (e.g. a method annotated with `@HandleEvent`) are fully customizable
using parameter resolvers. By default a couple of common parameter resolvers are registered to resolve the message
payload, raw message or its metadata.

By default, the first parameter of an annotated handler method is assumed to refer to the message payload.
Here’s a method with parameters for the payload, metadata and raw serialized message:

```java

@HandleEvent
void handle(UserCreated event, Metadata metadata, SerializedMessage message) {
    // do something special 
}
```

You can easily create your own parameter resolver. For instance, here’s one that will inject the message timestamp
to parameters of type `Instant`:

```java
public class TimestampParameterResolver implements ParameterResolver<DeserializingMessage> {
    @Override
    public Function<DeserializingMessage, Object> resolve(Parameter p) {
        if (p.getType().equals(Instant.class)) {
            return message -> Instant.of(message.getTimestamp());
        }
        return null;
    }
}
```

You can register your custom parameter resolver with the `FluxCapacitorBuilder` and then use it anywhere:

```java

@HandleEvent
void handle(UserCreated event, Instant timestamp) {
    // use the message time
}
```

### Message interceptors

Flux Capacitor client allows messages to be intercepted before they are handled or dispatched.
This can be useful in many situations. E.g. to check if a user is authorized to issue a command you can
write following interceptor:

```java
class AuthorizingInterceptor implements HandlerInterceptor {
    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage,
            Handler<DeserializingMessage> handler, String consumer) {
        return m -> {
            Sender sender = Sender.fromMetadata(m.getMetadata());
            RequiresRole requiresRole = m.getPayloadClass().getAnnotation(RequiresRole.class);
            if (requiresRole != null) {
                if (sender == null) {
                    throw new RequiresAuthenticationException(m.getPayload());
                }
                if (Arrays.stream(requiresRole.value()).noneMatch(sender::hasRole)) {
                    throw new UnauthorizedException(sender.getUserName(), m.getPayload());
                }
            }
            return function.apply(m);
        };
    }
}
```

Note that the `RequiresRole` annotation and `Sender` object in the above example are project specific and not
included in the client.

The next few sections discuss some supported functionalities that have been implemented using message interceptors.

### Data protection

Sometimes you are dealing with messages that contain sensitive information. This could be e.g. a credit card,
or the entire profile of a user. Flux Capacitor client enables this part of a message to be removed from the message
payload and stored inside the key-value store of the service where it can be easily encrypted or removed forever
if needed.

Here's an example of a command to create a user where the user profile is sensitive:

```java

@Value
class CreateUser {
    @ProtectData
    UserProfile userProfile;
    
    ...
}
```

Placing `@ProtectData` on a top level field of a message will place the user profile in the key value store under a
randomly generated key. By placing the key in the metadata of the message the value will be automatically restored
when the command is handled:

```java
class UserCommandHandler {
    @HandleCommand
    void handle(CreateUser command) {
        // user profile will be automatically injected into the command before handling
    }
}
```

When you no longer need the protected data you can have Flux Capacitor client delete it for you after handling
the message:

```java
class UserCommandHandler {
    @HandleCommand
    @DropProtectedData
    void handle(CreateUser command) {
        // user profile will be automatically injected into the command before handling
    }
}
```

This will drop the value from the key value store altogether without modifying the original message. Note that,
even though the value cannot be recovered after dropping it, the message is still available for handling. After the
data was dropped from the key-value store the field value will be `null`.

### Payload validation

By default, Flux Capacitor client performs constraint JSR380 validations of commands and queries. Here's a sample of an
annotated command:

```java

@Value
class CreateUser {
    @NotBlank
    String userId;

    @NotNull
    @Valid
    UserProfile userProfile;
}
```

If you don't want automatic payload validation you can disable it
using `FluxCapacitorBuilder.disablePayloadValidation()`.

### Correlating messages

By default, Flux Capacitor client correlates related messages. For example, if an event is published as result of the
handling of a command, then metadata is added to the event that allows you to correlate it to the original command.

Following this example the following metadata entries would be added to the event:

* `$correlationId`: the index of the command
* `$traceId`: the index of the message that triggered the command, or the index of the command if no other message
  triggered that command
* `$trigger`: the type of the command, e.g. `com.example.CreateUser`
* `$triggerRoutingKey`: the value of the routing key of the command if any

### Message routing

In case multiple clients of the same consumer are subscribed to the Flux Capacitor service, the service uses
the `segment`
of a message to route the message to one of the clients. This segment is automatically determined from the message id
if the message segment was not provided by the client. However, Flux Capacitor client can automatically calculate
a segment from the so-called routing key on a message.

Here's an example where the field `userId` is used to determine the routing key:

```java

@Value
class CreateUser {
    @RoutingKey
    String userId;

    UserProfile userProfile;
}
```

By annotating the `userId` field with `@RoutingKey` the client will automatically calculate a segment using consistent
hashing and pass it to the message. The advantage of this is that all
commands pertaining to a given user will be handled by the same client. This prevents conflicts that would arise when
multiple clients would modify the user aggregate simultaneously.

When listening to events there are situations where you'll need to handle every event. In that case you should use
the `@HandleNotification` annotation on your handler method instead of the default `@HandleEvent`.

### Metrics

Flux Capacitor service is automatically collecting metrics about connected clients. These metrics are stored as
messages like any other and can hence be tracked like any other message. Here's an example of metrics handler:

 ```java
 class MetricsHandler {
    @HandleMetrics
    void handle(ConnectEvent event) {
        ...
    }

    @HandleMetrics
    void handle(AppendEvent event) {
        ...
    }
}
 ```

Clients can also choose to publish custom metrics events. Some useful metrics collected on the client can be
enabled using `FluxCapacitorBuilder.collectTrackingMetrics()`. This will collect metrics about handler methods on the
client including the time it took to handle the message. This enables you to easily detect badly performing handlers.

### Miscellaneous

#### Low-level APIs

The `FluxCapacitor` interface provides a lower-level client API via `FluxCapacitor.client()` that allows
you to muddle with messages and data in serialized form. There may be situations where this will come in handy,
for instance when you want to handle messages in batch.

#### Timeouts

Queries and commands sent using send-and-wait will time out after some time. By default this timeout period is
1 minute. You can change the default timeout by annotating the query or command class as follows:

```java

@Timeout(300_000) //timeout of 5 minutes
class VerySlowQuery {
    ...
}
```

#### Message identity

All messages receive an identifier when they are created. By default new random UUID will be used as identifier. You
can use a custom `IdentityProvider` to supply the id by changing `Message.identityProvider` to something else.

#### Error tracking

Whenever a handle method gives rise to an exception the error will be published to a dedicated error log.
You can optionally these errors in a central location using `@HandleError`.

#### Testing with time

All messages receive a timestamp when they are published. That timestamp is determined using an internal clock
located on `SerializedMessage`. While testing it is often beneficial to fix the time. You can do so as
follows:

 ```java
 class SomeTest {
    @Test
    void testWithFixedTime() {
        var clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        try {
            SerializedMessage.useCustomClock(clock);
            //test something
        } finally {
            SerializedMessage.useDefaultClock();
        }
    }
}
 ```

Configuration
======================

You can easily override the default client configuration, just have a look at `FluxCapacitorBuilder`. Here are some of
the most important things you can do:

### Configuring message consumers

Consider the following configuration:

```java
fluxCapacitorBuilder
        .addConsumerConfiguration(MessageType.EVENT, ConsumerConfiguration.builder()
            .

name("webshop")
            .

handlerFilter(h ->h.

getClass().

getPackage().

getName().

startsWith("com.example.webshop"))
        .

trackingConfiguration(TrackingConfiguration.builder().

threads(4).

maxFetchBatchSize(128).

build())
        .

build());
``` 

This will configure a custom consumer for events. It will only contain handlers in the `com.example.webshop` package
or any of its sub-packages (handlers in other locations will use the default consumer). The consumer uses four threads
to fetch events and pass them to its handlers. Each thread will fetch no more than 128 events at a time.

This is just a sample of consumer settings that you can modify. For other settings check the Javadoc.

### Registering interceptors

Interceptors can be used to modify, block or record outgoing and incoming messages. They are the
equivalent of a common web filter in a CRUD application. Here's how to register interceptors:

```java
fluxCapacitorBuilder
        // registers an interceptor for outgoing messages
        .registerDispatchInterceptor(new SomeDispatchInterceptor())

        // registers an interceptor for incoming events
        .

registerHandlerInterceptor(new SomeEventHandlerInterceptor(),MessageType.EVENT);
```

### Overriding common settings

The client uses defaults that will be suitable for most applications.
However, to squeeze every ounce of performance or obtain additional insights
about your application you can disable or enable common settings:

```java
fluxCapacitorBuilder
        // don't perform a constraint validation check on incoming commands and queries 
        .disablePayloadValidation()

// store metrics about the performance of handlers in your application
   .

collectTrackingMetrics();
```

Bootstrap with Spring
======================

You can easily configure and start your app with spring (or spring boot).
All you need to do is import `FluxCapacitorSpringConfig` into your own configuration and
you're good to go:

```java

@Configuration
@Import(FluxCapacitorSpringConfig.class)
@ComponentScan
public class SampleSpringConfig {
}
```

To change the default config just inject and modify the `FluxCapacitorBuilder` instance:

```java

@Configuration
@Import(FluxCapacitorSpringConfig.class)
@ComponentScan
public class SampleSpringConfig {
    @Autowired
    public void configure(FluxCapacitorBuilder builder) {
        builder.collectTrackingMetrics()
                .addHandlerInterceptor(new AuthenticationInterceptor(), MessageType.COMMAND, MessageType.QUERY);
    }
}
```

By default the spring config will use an in-memory version of Flux Capacitor which is useful for testing but not for
anything else. To connect to a real Flux Capacitor service simply register a Client bean in your configuration:

```java

@Configuration
@Import(FluxCapacitorSpringConfig.class)
@ComponentScan
public class SampleSpringConfig {
    @Bean
    public Client fluxCapacitorClient() {
        return WebSocketClient.newInstance(new Properties("sample-app", System.getProperty("flux-capacitor-endpoint")));
    }
}
```

Spring beans with handler annotations like `@HandleEvent` are automatically registered for tracking.  
If you want to send a message just inject the `FluxCapacitor` instance and send the message:

```java
public class SpringExample {
    public static void main(final String[] args) {
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(SampleSpringConfig.class);
        applicationContext.getBean(FluxCapacitor.class).eventGateway().publish(new HelloWorld());
    }
}
```

Aside from detecting handlers Flux Capacitor also detects Upcasters automatically. If a spring bean has methods
annotated with `@Upcast` it will be automatically registered with the Serializer. 