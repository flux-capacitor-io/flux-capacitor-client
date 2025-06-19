<a href="https://flux-capacitor.io">
    <img src="https://flux-capacitor.io/assets/brand/flux-capacitor-white.svg" alt="Flux Capacitor logo" title="Flux Capacitor" align="right" height="60" />
</a>

# Flux Capacitor java client

[![Build](https://github.com/flux-capacitor-io/flux-capacitor-client/actions/workflows/deploy.yml/badge.svg)](https://github.com/flux-capacitor-io/flux-capacitor-client/actions)
[![Javadoc](https://img.shields.io/badge/javadoc-main-blue)](https://flux-capacitor.io/flux-capacitor-client/javadoc/apidocs/)
[![Maven Central](https://img.shields.io/maven-central/v/io.flux-capacitor/flux-capacitor-client)](https://central.sonatype.com/artifact/io.flux-capacitor/flux-capacitor-client?smo=true)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)

This repository contains the official Java client for [flux.host](https://flux.host), the Flux Capacitor platform. For a
short overview of functionalities, check out this [cheatsheet](documentation/cheatsheet.pdf).

---

## Installation

### Maven Users

Import the [Flux Capacitor BOM](https://mvnrepository.com/artifact/io.flux-capacitor/flux-capacitor-bom) in your
`dependencyManagement` section to centralize version management:

```xml

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.flux-capacitor</groupId>
            <artifactId>flux-capacitor-bom</artifactId>
            <version>${flux-capacitor.version}</version> <!-- See version badge above -->
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then declare only the dependencies you actually need (no version required):

```xml

<dependencies>
    <dependency>
        <groupId>io.flux-capacitor</groupId>
        <artifactId>java-client</artifactId>
    </dependency>
    <dependency>
        <groupId>io.flux-capacitor</groupId>
        <artifactId>java-client</artifactId>
        <classifier>tests</classifier>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.flux-capacitor</groupId>
        <artifactId>test-server</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.flux-capacitor</groupId>
        <artifactId>proxy</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> 💡 **Note:** The `test-server` and `proxy` modules are **optional**, and can be included if you want to test your
> application locally against an **in-memory Flux server**.

---

### Gradle Users

Use [platform BOM support](https://docs.gradle.org/current/userguide/platforms.html) to align dependency versions
automatically:

<details>
<summary><strong>Kotlin DSL (build.gradle.kts)</strong></summary>

```kotlin
dependencies {
    implementation(platform("io.flux-capacitor:flux-capacitor-bom:${fluxCapacitorVersion}"))
    implementation("io.flux-capacitor:java-client")
    testImplementation("io.flux-capacitor:java-client", classifier = "tests")
    testImplementation("io.flux-capacitor:test-server")
    testImplementation("io.flux-capacitor:proxy")
}
```

</details>

<details>
<summary><strong>Groovy DSL (build.gradle)</strong></summary>

```groovy
dependencies {
    implementation platform("io.flux-capacitor:flux-capacitor-bom:${fluxCapacitorVersion}")
    implementation 'io.flux-capacitor:java-client'
    testImplementation('io.flux-capacitor:java-client') {
        classifier = 'tests'
    }
    testImplementation 'io.flux-capacitor:test-server'
    testImplementation 'io.flux-capacitor:proxy'
}
```

</details>

---

### 🔖 Tip

You only need to update the version number **once** in the BOM reference. All included modules will automatically align
with it.

---

## Basic example

Create a new project and add an event class:

```java
public record HelloWorld() {
}
```

Create a handler for the event:

```java
public class HelloWorldEventHandler {
    @HandleEvent
    void handle(HelloWorld event) {
        System.out.println("Hello World!");
    }
}
```

Publish the event:

```java
public class ExampleMain {
    public static void main(final String[] args) {
        var fluxCapacitor = DefaultFluxCapacitor.builder()
                .build(LocalClient.newInstance());
        fluxCapacitor.registerHandlers(new HelloWorldEventHandler());
        fluxCapacitor.eventGateway().publish(new HelloWorld());
    }
}
```

Output:

```
Hello World!
```

### With Spring

Flux Capacitor integrates seamlessly with Spring. Here’s how the above example looks with Spring Boot:

```java

@SpringBootApplication
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

> ⚠️ Using Spring non-Boot? Add `@Import(FluxCapacitorSpringConfig.class)` to register FluxCapacitor and related beans.

### Testing your handler

Flux Capacitor includes a powerful TestFixture utility for testing your handlers without needing a full application
or infrastructure setup.

Here’s how to test our HelloWorldEventHandler from earlier:

```java
class HelloWorldEventHandlerTest {

    @Test
    void testHelloWorldHandler() {
        TestFixture.create(new HelloWorldEventHandler())
                .whenEvent(new HelloWorld())
                .expectThat(fc -> System.out.println(
                        "Event handled successfully!"));
    }
}
```

This will invoke your handler exactly like Flux Capacitor would in production, but entirely in memory and synchronously
by default.

> ✅ We’ll explore more powerful testing patterns — including assertions on results, published commands, exceptions,
> and full event flows — later in this guide.

---

# Features

The java client supports all features of Flux Capacitor but also offers plenty of additional functionality.
What follows is a summary of the most important features.

## Table of Contents

### 📦 Messaging

- [Message Handling](#message-handling)
- [Tracking Messages](#tracking-messages)
- [Message Replays](#message-replays)
- [Dynamic Dead Letter Queue (DLQ)](#dynamic-dead-letter-queue-dlq)
- [Local Handlers](#local-handlers)
- [Payload Validation](#payload-validation)
- [User and Role-Based Access Control](#user-and-role-based-access-control)
- [Scheduling](#scheduling)
- [User-defined message logs](#user-defined-message-logs)
- [Dispatching Messages](#dispatching-messages)
- [Testing your Handlers](#testing-your-handlers)
- [Handling Web Requests](#handling-web-requests)
- [Serving Static Files](#serving-static-files)
- [Handling WebSocket Messages](#handling-websocket-messages)
- [Testing Web Endpoint Behavior](#testing-web-endpoint-behavior)
- [Outbound Web Requests](#outbound-web-requests)
- [Metrics Messages](#metrics-messages)

### 🧠 Domain Modeling, Persistence, and Search

- [Domain Modeling](#domain-modeling)
- [Applying Updates to Entities](#applying-updates-to-entities)
- [Nested Entities](#nested-entities)
- [Model Persistence](#model-persistence)
- [Stateful Handlers](#stateful-handlers)
- [Document Indexing and Search](#document-indexing-and-search)
- [Tracking and Updating Documents](#tracking-and-updating-documents)

### 🛡️ Data Handling and Serialization

- [Protecting Sensitive Data](#protecting-sensitive-data)
- [Serialization, Upcasting, and Downcasting](#serialization-upcasting-and-downcasting)
- [Filtering Object Content](#filtering-object-content)
- [Kotlin Support](#kotlin-support)

### 🛠️ Configuration and Extensibility

- [Configuring Application Properties](#configuring-application-properties)
- [Parameter Injection with Custom Resolvers](#parameter-injection-with-custom-resolvers)
- [Interceptors: Dispatching, Handling, and Batching](#interceptors-dispatching-handling-and-batching)
- [Configuring Flux Capacitor](#configuring-flux-capacitor)
- [WebSocketClient: Connect to the Flux Platform](#websocketclient-connect-to-the-flux-platform)
- [Compatibility and Dependencies](#compatibility-and-dependencies)

---

## Message Handling

Flux Capacitor is centered around sending and receiving messages — such as __commands__, __events__, __queries__, and
__web requests__. These messages can originate from your own application or any other client connected to the same Flux
Platform.

Handlers are simply methods annotated with `@HandleCommand`, `@HandleEvent`, `@HandleQuery`, etc. Here’s a basic example
of an event handler that dispatches a command to send a welcome email when a user is created:

```java
class UserEventHandler {
    @HandleEvent
    void handle(CreateUser event) {
        FluxCapacitor.sendCommand(
                new SendWelcomeEmail(event.getUserProfile()));
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
        UserProfile userProfile = FluxCapacitor
                .queryAndWait(new GetUserProfile(event.getUserId()));
        // Perform reset using userProfile
    }
}
```

### Returning Futures

Handler methods may also return a `CompletableFuture<T>` instead of a direct value. In that case, Flux Capacitor will
publish the result to the result log once the future completes:

```java

@HandleQuery
CompletableFuture<UserProfile> handle(GetUserProfile query) {
    return userService.fetchAsync(query.getUserId());
}
```

This can be useful when calling asynchronous services (e.g. via HTTP or database drivers).

> ⚠️ **Caution:** While supported, returning a future means Flux will consider the message *handled* as soon as the
> handler returns the future—not when the future completes. This can be problematic if:
>
> - The operation must be guaranteed to complete (e.g. business-critical updates),
> - You rely on message acknowledgment for progress tracking,
> - Or you need back-pressure to avoid overloading the system.
>
> For most handlers, **synchronous return types are recommended**, or use `.join()` to explicitly block when necessary.

This pattern works best for non-critical side effects or purely read-oriented queries that can tolerate eventual
completion.

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
    UserAccount handle(GetUser query) {
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

- The `handle(...)` method produces the result.
- The `record(...)` method logs the query,

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

## Tracking Messages

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

| Setting      | Default Value |
|--------------|---------------|
| threads      | 1             |
| maxFetchSize | 1024          |

These defaults are sufficient for most scenarios. You can always override them for improved performance or control.

---

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
```

---

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

---

## Local handlers

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

---

## Payload Validation

Flux Capacitor automatically validates incoming request payloads using [JSR 380](https://beanvalidation.org/2.0/)
(Bean Validation 2.0) annotations.

This includes support for:

- `@NotNull`, `@NotBlank`, `@Size`, etc.
- `@Valid` on nested objects
- Constraint violations in command/query/webrequest payloads

If a constraint is violated, the handler method is **never called**. Instead, a `ValidationException`,
is thrown before the handler is invoked.

```java

public record CreateUser(@NotBlank String userId,
                         @NotNull @Valid UserProfile profile) {
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

---

## User and Role-Based Access Control

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

### Best Practices

- Use role annotations on **payload classes** to guarantee strict access checks in all environments.
- Use them on **handlers** to control fallback behavior or define role-specific processing.
- Set security defaults on **packages** or base classes, and override selectively.
- Define **custom annotations** to avoid scattering string-based role declarations.

---

> 💡 **Tip:** Access control is enforced transparently — there’s no need to log or repeat the user or message context.
> Flux automatically maintains correlation metadata between the original request and any errors, logs, or events that
> follow.

---

### Where does user info come from?

User roles are resolved by the configured `UserProvider`, which extracts the current user from message metadata (e.g.,
authentication tokens, headers, etc.). By default, Flux Capacitor uses a pluggable SPI to register this provider.

> 💡 You can override or mock this provider in tests using the TestFixture API.

---

### Providing Your Own User Logic

You can implement a custom `UserProvider` to extract users from headers, JWT tokens, cookies, etc.

```java
public class MyUserProvider extends AbstractUserProvider {
    public MyUserProvider() {
        super("Authorization", MyUser.class); // metadata key and user type
    }

    @Override
    public User fromMessage(HasMessage message) {
        if (message.toMessage() instanceof WebRequest request) {
            return decodeToken(request.getHeader("Authorization"));
        }
        return super.fromMessage(message);
    }

    private User decodeToken(String header) {
        // Implement your own token decoding and verification logic here
        return ...;
    }
}
```

This allows you to inject meaningful, application-specific `User` objects into your handlers.

---

### System and Testing Support

Your `UserProvider` implementation can also support testing and system behavior by implementing:

- `getSystemUser()` — returns a default **system-level user**, used:
    - as the default actor in tests,
    - when publishing system-side effects (e.g. from scheduled handlers).
- `getUserById(...)` — resolves a user by ID, used in test utilities like `fixture.whenCommandByUser(...)`.

This ensures your custom user logic is consistently applied, even in automated tests and background execution.

---

### 🔧 Registering your UserProvider

To enable your custom `UserProvider` (e.g., for authenticating users via headers or tokens), register it using Java's
Service Provider mechanism.

Create the file:

```
src/main/resources/META-INF/services/io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider
```

List your implementation classes (one per line) in order of preference:

```
com.example.authentication.SenderProvider
com.example.authentication.SystemUserProvider
```

Flux Capacitor will automatically discover and register them at startup.

> 💡 If you're using Spring, your `UserProvider` can also be exposed as a bean — Flux Capacitor will pick it up
> automatically.
>
> ⚠️ However, tests **not using Spring** will not pick up the bean. For test scenarios or CLI usage, the SPI mechanism
> is still the easiest.

---

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
        FluxCapacitor.cancelSchedule(
                "AccountClosed-" + event.getUserId());
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

@Periodic(delay = 5, timeUnit = TimeUnit.MINUTES)
public record RefreshData(String index) {
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

---

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

---

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

---

## Testing your Handlers

Flux Capacitor comes with a flexible, expressive testing framework based on the given-when-then pattern. This enables
writing behavioral tests for your handlers without needing to mock the infrastructure.

Here’s a basic example:

```java
TestFixture testFixture = TestFixture.create(new UserEventHandler());

@Test
void newUserGetsWelcomeEmail() {
    testFixture.whenEvent(new CreateUser(userId, myUserProfile))
            .expectCommands(new SendWelcomeEmail(myUserProfile));
}
```

This test ensures that when a `CreateUser` event occurs, a `SendWelcomeEmail` command is issued by the handler.

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
       .expectCommands(SendWelcomeEmail.class, 
                       isA(AddUserToOrganization.class));
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

This example first triggers a `CreateUser` command, expects a `SendWelcomeEmail` event, and then issues a `GetUser`
query,
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

### Providing External JSON Files

Test fixtures support loading inputs from external JSON resources. This allows you to keep your tests clean and reuse
structured input data.

Any `givenXyz(...)`, `whenXzy(...)`, or `expectXyz(...)` method argument that is a `String` ending with `.json` will be
interpreted as a **classpath resource path**, and deserialized accordingly.

For example:

[//]: # (@formatter:off)
```java
fixture.givenCommands("create-user.json")
    .whenQuery(new GetUser(userId))
    .expectResult("user-profile.json");
```
[//]: # (@formatter:on)

If your test class is in the `org.example` package, this will resolve to `/org/example/create-user.json` in the
classpath, unless the JSON path is absolute (starts with `/`), e.g.:

```java
fixture.givenCommands("/users/create-user.json");
```

#### Class Resolution with `@class`

Each JSON file must include a `@class` property to enable deserialization:

```json
{
  "@class": "org.example.CreateUser",
  "userId": "3290328",
  "email": "foo.bar@example.com"
}
```

If your classes or packages are annotated with `@RegisterType`, you can use **simple class names**:

```json
{
  "@class": "CreateUser"
}
```

Or partial paths:

```json
{
  "@class": "example.CreateUser"
}
```

> This enables readable and concise references while still resolving to the correct type.

#### Inheriting from Other JSON Files

JSON resources can **extend** other resources using the `@extends` keyword:

```json
{
  "@extends": "create-user.json",
  "details": {
    "lastName": "Johnson"
  }
}
```

This will recursively merge the referenced file (`/org/example/create-user.json`) with the current one, allowing you
to override or augment deeply nested structures.

> 🧠 This is especially useful for composing test scenarios with shared defaults or inheritance-like setups.

---

### Adding or asserting metadata

Wrap your payload in a Message to add or assert metadata:

```java

@Test
void newAdminGetsAdditionalEmail() {
    testFixture.whenCommand(new Message(new CreateUser(...),
    Metadata.of("roles", Arrays.asList("Customer", "Admin"))))
        .expectCommands(new SendWelcomeEmail(...),
    new SendAdminEmail(...));
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
fixture.whenCommand("create-user-pete.json")
       .expectThat(fc -> Mockito.verify(emailService).sendEmail(...));
```
[//]: # (@formatter:on)

### Triggering side effects manually

Use `whenExecuting()` to test code outside of message dispatching, like HTTP calls:

[//]: # (@formatter:off)
```java
fixture.whenExecuting(fc -> httpClient.put("/user", "/users/user-profile-pete.json"))
       .expectEvents("create-user-pete.json");
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
> would in production.

### Using Test Fixtures in Spring

Flux Capacitor provides seamless integration with Spring Boot for testing. You can inject a `TestFixture` directly:

```java

@SpringBootTest
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

- ✅ **Spring Boot**: No manual setup needed—`TestFixture` is auto-configured.
- ⚠️ **Spring Core (non-Boot)**: Manually import the test configuration:

  ```java
  @Import(FluxCapacitorTestConfig.class)
  ```

  This ensures the `TestFixture` and related infrastructure are available in the Spring context.

---

#### Switching to Synchronous Mode

By default, the injected fixture is **asynchronous**. To use a **synchronous** fixture instead:

##### Globally via `application.properties`:

```properties
fluxcapacitor.test.sync=true
```

##### Or per test class:

```java

@TestPropertySource(properties = "fluxcapacitor.test.sync=true")
@SpringBootTest
class SyncAppTest {

    @Autowired
    TestFixture fixture;

    // test logic...
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
            .givenCommands(new CreateUser(myUserProfile),
                           new CloseAccount(userId))
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
            .givenCommands(new CreateUser(myUserProfile),
                           new CloseAccount(userId),
                           new ReopenAccount(userId))
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

---

## Handling Web Requests

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
public List<UserAccount> listUsers() {
    return userService.getAllUsers();
}
```

This will match incoming GET requests to `/users` and return a list of users. The response is published back as a
`WebResponse`.

You can use the general `@HandleWeb` if you want to match multiple paths or methods or define a custom HTTP method:

```java

@HandleWeb(value = "/users/{userId}", method = {"GET", "DELETE"})
public CompletableFuture<?> handleUserRequest(WebRequest request, @PathParam String userId) {
    return switch (request.getMethod()) {
        case "GET" -> FluxCapacitor.query(new GetUser(userId));
        case "DELETE" -> FluxCapacitor.sendCommand(new DeleteUser(userId));
        default -> throw new UnsupportedOperationException();
    };
}
```

> 💡 **Tip:** To match any HTTP method without listing them explicitly, use `HttpRequestMethod.ANY`.
> This is equivalent to `"*"` and will match all incoming requests for the given path.

```java

@HandleWeb(value = "/users/{userId}", method = HttpRequestMethod.ANY)
public CompletableFuture<?> handleAllUserMethods(
        WebRequest request, @PathParam String userId) {
    // Handle any method (GET, POST, DELETE, etc.)
    ...
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
public UserAccount getUser(@PathParam String id) {
    return userService.get(id);
}
```

If the `value` is left empty, the framework will use the parameter name (`id` in this case).

#### Other Parameter Annotations

In addition to `@PathParam`, you can extract other values from the request using:

- `@QueryParam` – extract query string values
- `@HeaderParam` – extract HTTP headers
- `@CookieParam` – extract cookie values
- `@FormParam` – extract form-encoded values (for POST/PUT)

Each of these annotations supports the same rules:

- If no name is given, the method parameter name is used
- Values are automatically converted to the target parameter type

#### URI Prefixing and Composition with `@Path`

The `@Path` annotation can be used at the **package**, **class**, **method**, or **property** level to construct URI
path segments. Paths are chained together from the outermost package to the innermost handler method. If a path segment
starts with `/`, it resets the chain from that point downward.

- Empty path values use the simple name of the package.
- If placed on a field or getter, the property value is used as the path segment (enabling dynamic routing).

```java
@Path
package my.example.api;

@Path("users")
public class UserHandler {

    @Path("{id}")
    @HandleGet
    public User getUser(@PathParam String id) { ...}
}
```

This matches `/api/users/{id}`. If the class annotation had started with a `/`, i.e.: had been `@Path("/users")`, the
pattern would have become `/users/{id}`.

---

## Serving Static Files

Flux Capacitor supports serving static files directly from a handler class by using the `@ServeStatic` annotation.
This allows client applications to expose static resources (HTML, JS, CSS, images, etc.) without needing an external web
server.

```java

@ServeStatic(value = "/web", resourcePath = "/static")
public class WebAssets {
}
```

This will serve all files under `/static` (from the classpath or file system) under the URI path `/web`.

### Features

- Supports both **file system** and **classpath** resources
- Optional **fallback file** (e.g. for single-page apps)
- Automatic `Cache-Control` headers
- Compression via Brotli and GZIP (if precompressed variants exist)

### Annotation Reference

```java
@ServeStatic(
        value = "/assets",
        resourcePath = "/public",
        fallbackFile = "index.html",
        immutableCandidateExtensions = {"js", "css", "svg"},
        maxAgeSeconds = 86400
)
```

#### Parameters:

- `value`: Web path(s) where static content is served. Relative paths are prefixed by `@Path` values.
- `resourcePath`: The resource root (either on the file system or classpath).
- `fallbackFile`: A file to serve when the requested path doesn’t exist (e.g. `index.html`). Set to `""` to disable.
- `immutableCandidateExtensions`: Extensions that are eligible for aggressive caching if fingerprinted (e.g.
  `main.123abc.js`).
- `maxAgeSeconds`: Default cache duration for non-immutable resources.

### Example: Serving a React App

```java
@ServeStatic(value = "/app", resourcePath = "/static", fallbackFile = "index.html")
public class WebFrontend { ... }
```

This will serve files under `/app/**` and fallback to `index.html` for unknown paths—ideal for single-page apps.

> 📁 Files in `/static` on the classpath (e.g. under `resources/static/`) or `/static` on disk will be served.

> When both file system and classpath contain a file, the **file system takes precedence**.

> If the `resourcePath` starts with `classpath:`, **only classpath resources** are served.  
> If it starts with `file:`, **only file system resources** are served.

This allows precise control over where content is loaded from and ensures classpath-only or file-system-only resolution depending on the use case.

#### Combining Static and Dynamic Handlers

You can freely combine `@ServeStatic` with dynamic handler methods in the same class or package.

This is especially useful when your application serves a combination of:

- **Static assets** like HTML, JS, CSS
- **Dynamic endpoints** like REST APIs or view-rendered pages

```java
@Path("/app")
@ServeStatic("static") // serves static files from the /static resource directory for web paths /app/static/**
public class AppController {

    @HandleGet("/status")
    Status getStatus() {
        return new Status("OK", Instant.now());
    }

    @HandlePost("/submit")
    SubmissionResult submitForm(FormData data) {
        return formService.handle(data);
    }
}
```

This will:

- Serve `/app/static/index.html`, `/app/static/styles.css`, etc. from the `static/` resource directory
- Also respond to `/app/status` and `/app/submit` dynamically

The static file handling applies to all routes **not matched** by other methods in the class. This makes it ideal for combining SPAs or hybrid web apps with API endpoints under a shared route prefix.

---

## Handling WebSocket Messages

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

> ℹ️ Like other handlers, socket endpoints may be annotated with `@Consumer` for tracking isolation.

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

| Annotation                 | Description                                        |
|----------------------------|----------------------------------------------------|
| `@HandleSocketOpen`        | Handles WebSocket connection open                  |
| `@HandleSocketMessage`     | Handles incoming text or binary WebSocket messages |
| `@HandleSocketPong`        | Handles pong responses (usually for health checks) |
| `@HandleSocketClose`       | Handles WebSocket session closing                  |
| `@HandleSocketHandshake`   | Allows customizing the handshake phase             |
| `@SocketEndpoint`          | Declares a per-session WebSocket handler class     |
| `SocketSession` (injected) | Controls sending messages, pinging, and closing    |

Flux Capacitor makes WebSocket communication secure, observable, and composable—integrated seamlessly into your
distributed, event-driven architecture.

---

## Testing Web Endpoint Behavior

Flux Capacitor allows you to simulate and verify HTTP interactions as part of your test flows. Web requests
can be tested like any other command, query, or event.

Here’s a complete test for a `POST /games` handler that accepts a JSON request and publishes a command:

```java

@Test
void registerGame() {
    testFixture
            .whenPost("/games", "/game/game-details.json")  // Simulates POST with payload
            .expectResult(GameId.class)                     // Asserts a result is returned
            .expectEvents(RegisterGame.class);              // Asserts a command was published
}
```

The corresponding handler is:

```java

@HandlePost("/games")
CompletableFuture<GameId> addGame(GameDetails details) {
    return FluxCapacitor.sendCommand(new RegisterGame(details));
}
```

As always, the `.json` file is automatically loaded from the classpath, allowing you to cleanly separate test data:

📄 `/game/game-details.json`

```json
{
  "title": "Legend of the Skylands",
  "description": "An epic singleplayer adventure with puzzles and secrets.",
  "releaseDate": "2025-11-12T00:00:00Z",
  "tags": [
    "adventure",
    "puzzle",
    "singleplayer"
  ]
}
```

---

#### Example: Querying with `GET`

You can test GET endpoints just as easily. This example first registers a game via `POST /games`, then fetches the list
of all games via `GET /games` and checks the result:

```java

@Test
void getGames() {
    testFixture
            .givenPost("/games", "/game/game-details.json")   // Precondition: register a game
            .whenGet("/games")                                // Perform GET request
            .<List<Game>>expectResult(r -> r.size() == 1);    // Assert one game is returned
}
```

This corresponds to the following handler method:

```java

@HandleGet
@Path("/games")
CompletableFuture<List<Game>> getGames(@QueryParam String term) {
    return FluxCapacitor.query(new FindGames(term));
}
```

---

#### ℹ️ Path Parameter Substitution in Tests

When simulating web requests, Flux Capacitor automatically substitutes `{...}` placeholders in the request path using
results from previous steps:

- The result of the **first `when...()` step** is saved when `.andThen()` is called.
- If a subsequent path (e.g. `/games/{gameId}/buy`) contains placeholders, Flux Capacitor will:
- Attempt to replace each placeholder (like `{gameId}`) with the string value of a previously returned result.
- Track all resolved placeholders across steps. This allows chaining:

[//]: # (@formatter:off)
```java
testFixture.whenPost("/games", "/game/game-details.json") // returns gameId
     .andThen()
     .whenPost("/games/{gameId}/buy") // uses gameId, returns orderId
     .andThen()
     .whenPost("/games/{gameId}/refund/{orderId}"); // uses gameId from step 1, orderId from step 2
```
[//]: # (@formatter:on)

Substitutions are based purely on the order of results, not types or field names. The `.toString()` value of each result
is used to fill the next unresolved placeholder.

This keeps tests expressive and avoids boilerplate, especially for flows that involve chained resource creation (e.g.
`POST /games → POST /games/{gameId}/buy → ...`).

---

## Outbound Web Requests

Flux Capacitor provides a unified API for sending HTTP requests through the `WebRequestGateway`.

Unlike traditional HTTP clients, Flux logs outbound requests as `WebRequest` messages. These are then handled by:

- A **local handler** that tracks requests if the URL is **relative**, or
- A **connected remote client or proxy**, if the URL is **absolute**.

### Sending a Request

```java
WebRequest request = WebRequest.get("https://api.example.com/data")
        .header("Authorization", "Bearer token123")
        .build();

WebResponse response = FluxCapacitor.get()
        .webRequestGateway().sendAndWait(request);

String body = response.getBodyString();
```

> ✅ All outbound traffic is logged and traceable in the Flux platform.

### Asynchronous and Fire-and-Forget

You can send requests asynchronously:

[//]: # (@formatter:off)
```java
FluxCapacitor.get().webRequestGateway()
        .send(request)
        .thenAccept(response -> log
              .info("Received: {}",response.getBodyString()));
```
[//]: # (@formatter:off)

Or fire-and-forget:

[//]: # (@formatter:off)
```java
FluxCapacitor.get().webRequestGateway()
        .sendAndForget(Guarantee.STORED, request);
```
[//]: # (@formatter:on)

### Relative vs Absolute URLs

Flux supports both local and remote handling:

- **Absolute URLs** (e.g., `https://...`): The request is forwarded via the Flux **Web Proxy** and executed externally.
- **Relative URLs** (e.g., `/internal/doSomething`): The request is routed to a handler within another connected Flux
  application.

This allows decoupled request-response workflows across services and environments.

### Isolating Traffic with `consumer`

You can specify a `consumer` in the request settings:

```java
WebRequestSettings settings = WebRequestSettings.builder()
        .consumer("external-api-xyz")
        .timeout(Duration.ofSeconds(5))
        .build();
```

When set, the Flux Web Proxy will isolate this request in its own internal processing pipeline. This is useful when you:

- Want to isolate third-party integrations (e.g., API rate limits)
- Need different retry or error handling strategies per destination
- Want fault isolation between outgoing endpoints

### Mocking External Endpoints in Tests

Flux makes it easy to test full workflows—including outbound `WebRequest` calls—by **mocking** the response during
tests:

```java
static class EndpointMock {
    @HandleGet("https://api.example.com/1.1/locations")
    WebResponse handleLocations() {
        return WebResponse.builder()
                .header("X-Limit", "100")
                .payload("/example-api/get-locations.json")
                .build();
    }
}

@Test
void testGetLocations() {
    TestFixture.create(new EndpointMock())
            .whenGet("https://api.example.com/1.1/locations")
            .<List<ExampleLocation>>expectResult(r -> r.size() == 2);
}
```

You can match requests by:

- Method and URL
- Headers, body, or any other property

> ✅ This gives you **full end-to-end test coverage**, even when integrating with external APIs.

---

### Summary

- ✅ Use `WebRequest` for centralized, traceable outbound HTTP calls.
- ✅ Automatically routes to a proxy or local handler depending on URL.
- ✅ Supports timeouts, consumers, and structured request settings.
- ✅ Easily mock remote endpoints for testing full business flows.

---

## Metrics Messages

Flux Capacitor supports a built-in message type for metrics: `MessageType.METRICS`.  
These messages provide a powerful way to observe and trace system behavior across clients, handlers, and infrastructure.

Metrics messages are:

- Lightweight, structured, and traceable
- Logged like any other message
- Routable to handlers (via `@HandleMetrics`)
- Stored by default for **1 month** due to volume

---

### Publishing Metrics

You can publish metrics manually using the `FluxCapacitor.publishMetrics(...)` method:

```java
FluxCapacitor.publishMetrics(new SystemMetrics("slowProjection", "thresholdExceeded"));
```

This emits a structured metrics message to the metrics topic.

All metrics are wrapped in a regular `Message`, so you can include metadata or delivery guarantees:

[//]: # (@formatter:off)
```java
FluxCapacitor.get()
    .metricsGateway()
    .publish(new MyMetric("foo"), 
             Metadata.of("critical","true"),
             Guarantee.STORED);
```
[//]: # (@formatter:off)

---

### Automatic Metrics from Clients

Many metrics are automatically emitted by the Flux Java client:

- **Connect / Disconnect events** when clients (re)connect
- **Tracking updates** (throughput, handler times, latency)
- **Search / state / document store operations**
- **Web request round-trip timings**

These are particularly helpful in troubleshooting or auditing system performance.

---

### Consuming Metrics

You can treat metrics like any other message type:

```java

@HandleMetrics
void on(MetricEvent event) {
    log.debug("Observed metric: {}", event);
}
```

Use this to power custom dashboards, counters, diagnostics, or trigger alerts.

---

### Disabling Metrics

To reduce noise or overhead, you can selectively disable automatic metrics:

#### Disable per handler or batch using an interceptor

```java

@Consumer(handlerInterceptors = DisableMetrics.class)
public class SilentHandler {
    @HandleEvent
    void on(MyEvent event) { ... }
}
```

You can also use `batchInterceptors` to disable metrics for an entire consumer instance.

#### Disable globally per client

If you're instantiating a `WebSocketClient`, you can pass `disableMetrics = true` via the client config.

#### Use programmatic interceptors

If needed, you can suppress metric dispatch programmatically using:

[//]: # (@formatter:off)
```java
AdhocDispatchInterceptor.runWithAdhocInterceptor(() -> {
    // your code here
}, (message, messageType, topic) -> null, MessageType.METRICS);
```
[//]: # (@formatter:on)

---

### Common Use Cases

- **Audit debugging**: trace which handler caused a slowdown
- **Observability**: track real-time stats like search throughput
- **Dashboards**: expose per-entity or per-consumer metrics
- **Trigger alerting**: when retries or handler delays exceed thresholds

Metrics messages provide lightweight hooks into system behavior — use them for visibility without overhead.

---

## Domain Modeling

Flux Capacitor allows you to model the state of your domain using entities that evolve over time by applying updates.
These entities — such as users, orders, etc. — maintain state and enforce invariants through controlled updates,
typically driven by commands.

### Defining the Aggregate Entity

To define a stateful domain object, annotate it with `@Aggregate`:

```java

@Aggregate
@Builder(toBuilder = true)
public record UserAccount(@EntityId UserId userId,
                          UserProfile profile,
                          boolean accountClosed) {
}
```

This `UserAccount` class models an aggregate with state such as `profile` and `accountClosed`. Each entity may contain a
field
annotated with `@EntityId` that acts as a unique identifier. For aggregates, this is optional — the aggregate itself is
typically loaded using `FluxCapacitor.loadAggregate(id)`.

An **aggregate** is a specialized root entity that serves as an entry point into a domain model. It may contain nested
child entities (modeled via `@Member`), but represents a single unit of consistency.

### 💡 **Tip:** Use strongly typed identifiers

While `@EntityId` can be placed on any object (e.g. a `String` or `UUID`), consider using a strongly typed `Id<T>` class
instead. This lets you:

- Enforce consistent ID prefixes (e.g. `"user-"`)
- Avoid collisions between entities with the same functional ID
- Enable case-insensitive matching (optional)
- Retain type information for safer entity loading and deserialization

```java
class UserId extends Id<UserAccount> {
    public UserId(String value) {
        super(value, "user-");
    }
}

@Aggregate
public record UserAccount(@EntityId UserId userId) {
}
```

Now you can easily load the entity via:

```java
Entity<UserAccount> user = FluxCapacitor.loadAggregate(new UserId("1234"));
```

---

### Applying Updates to entities

Entities evolve in response to **updates** — typically the payload of a command. Updates define what change should
happen and contain the logic to validate and apply those changes.

Here’s an example using two commands — one to create a user, and another to update their profile.

```java

public record CreateUser(UserId userId,
                         UserProfile profile) {

    @AssertLegal
    void assertNotExists(UserAccount current) {
        throw new IllegalCommandException("Account already exists");
    }

    @Apply
    UserAccount apply() {
        return new UserAccount(userId, profile, false);
    }
}
```

This update creates a new `UserAccount` entity after checking that no user with the same ID currently exists.

```java

public record UpdateProfile(UserId userId,
                            UserProfile profile) {

    @AssertLegal
    void assertExists(@Nullable UserAccount current) {
        if (current == null) {
            throw new IllegalCommandException("Account not found");
        }
    }

    @AssertLegal
    void assertAccountNotClosed(UserAccount current) {
        if (current.isAccountClosed()) {
            throw new IllegalCommandException("Account is closed");
        }
    }

    @Apply
    UserAccount apply(UserAccount current) {
        return current.toBuilder().profile(profile).build();
    }
}
```

This update first ensures the user exists and their account isn’t closed before applying the change.

> **Note**: Handler method parameters (like `UserAccount current`) are only injected if non-null. Use `@Nullable`
> to allow for missing values.

---

### Intercepting and Transforming Updates

In addition to applying and validating updates, you can also **intercept** them *before* they reach the legal or apply
phase.

Use `@InterceptApply` to:

- Suppress updates that are irrelevant or no-ops
- Rewrite updates that would otherwise be invalid
- Split a single update into multiple smaller updates

```java

@InterceptApply
Object ignoreNoChange(UserAccount current) {
    if (current.getProfile().equals(profile)) {
        return null; // suppress update, nothing to change
    }
    return this;
}
```

You can even rewrite the update entirely:

```java

@InterceptApply
UpdateProfile downgradeCommand(CreateUser command, UserAccount current) {
    //the current account could be injected, hence it already exists
    return new UpdateProfile(command.getUserId(), command.getProfile());
}
```

Or expand a bulk command into many atomic ones:

```java

@InterceptApply
List<CreateTask> expandBulk(BulkCreateTasks bulk) {
    return bulk.getTasks();
}
```

> 🔁 Flux will apply interceptors **recursively** until the final update no longer changes.

---

### Invocation Order

The update lifecycle flows as follows:

1. **Intercept** using `@InterceptApply`
2. **Assert legality** using `@AssertLegal`
3. **Apply changes** using `@Apply`

This allows you to rewrite or suppress updates *before* they’re validated or stored — a powerful tool for protecting
data integrity and simplifying update logic.

### Return Values

`@InterceptApply` supports flexible return types:

- `null` or `void` → suppress the update
- `this` → no change
- A **new update object** → rewrites the update
- A **Collection**, `Stream`, or `Optional` → emits zero or more new updates

> 📌 You typically don’t need to intercept just to avoid storing no-ops. Instead, annotate the `@Aggregate` or `@Apply`
> method with `eventPublication = IF_MODIFIED` to avoid persisting unchanged state.

---

### Summary

| Annotation        | Purpose                                        | Phase        |
|-------------------|------------------------------------------------|--------------|
| `@InterceptApply` | Rewrite, suppress, or expand updates           | Pre-check    |
| `@AssertLegal`    | Validate preconditions                         | Validation   |
| `@Apply`          | Apply state transformation                     | Execution    |

Together, these annotations offer full control over your entity update lifecycle — with a clean, declarative style.

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
@Builder(toBuilder = true)
public record UserAccount(@EntityId UserId userId,
                          UserProfile profile,
                          boolean accountClosed) {

    @AssertLegal
    static void assertNotExists(CreateUser update, @Nullable UserAccount user) {
        if (user != null) {
            throw new IllegalCommandException("Account already exists");
        }
    }

    @Apply
    static UserAccount create(CreateUser update) {
        return new UserAccount(update.getUserId(), update.getProfile(), false);
    }

    @AssertLegal
    static void assertExists(UpdateProfile update, @Nullable UserAccount user) {
        if (user == null) {
            throw new IllegalCommandException("Account does not exist");
        }
    }

    @AssertLegal
    void assertAccountNotClosed(UpdateProfile update) {
        if (accountClosed) {
            throw new IllegalCommandException("Account is closed");
        }
    }

    @Apply
    UserAccount update(UpdateProfile update) {
        return toBuilder().profile(update.getProfile()).build();
    }
}
```

In this model, the `UserAccount` aggregate handles all validation and transformation logic. Over time, this
centralization leads to bloat and tight coupling — especially in larger systems with many features.

---

### Mixing strategies

Flux Capacitor allows **mixed approaches**. You can define:

- `@AssertLegal` methods on the command payload
- `@Apply` methods inside the entity
- or vice versa

Just keep in mind: logic that lives in updates is **easier to test, extend, and remove**.

---

## Applying Updates to Entities

To change the state of an entity, use `FluxCapacitor.loadAggregate(...)` to retrieve the aggregate and apply updates to
it.

Here's a basic example of a command handler applying a `CreateUser` update:

```java
public class UserCommandHandler {
    @HandleCommand
    void handle(CreateUser command) {
        FluxCapacitor.loadAggregate(command.getUserId(), UserAccount.class).assertAndApply(command);
    }
}
```

This loads the `UserAccount` entity by ID and applies the `CreateUser` command. Internally, Flux Capacitor will:

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

---

## Nested Entities

Flux Capacitor allows aggregates to contain nested entities — for example, users with authorizations or orders with line
items. These nested entities can be added, updated, or removed using the same `@Apply` pattern used for root aggregates.

To define a nested structure, annotate the collection or field with `@Member`:

```java

@Aggregate
@Builder(toBuilder = true)
public record UserAccount(@EntityId UserId userId,
                          UserProfile profile,
                          boolean accountClosed) {

    @Member
    List<Authorization> authorizations;
}
```

Child entities must define their own `@EntityId`:

```java

public record Authorization(@EntityId AuthorizationId authorizationId,
                            Grant grant) {
}
```

### Adding a Child Entity

To add a nested entity like `Authorization`, simply return a new instance from the `@Apply` method:

```java

public record AuthorizeUser(AuthorizationId authorizationId,
                            Grant grant) {

    @Apply
    Authorization apply() {
        return new Authorization(authorizationId, grant);
    }
}
```

The `UserAccount` aggregate is automatically updated to include this new child entity.

### Removing a Child Entity

To remove a nested entity, return `null` from the `@Apply` method:

```java

public record RevokeAuthorization(AuthorizationId authorizationId) {

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

> ⚠️ **Note:** When a child entity is added, updated, or removed using an `@Apply` method, Flux Capacitor will:
>
> - Automatically **locate the parent aggregate**
> - Apply the update to the child entity
> - And return a **new instance of the parent** (if it's immutable), with the updated child state included
>
> This allows you to use immutable models (e.g. Java records or classes with Lombok’s `@Value`) without extra
> boilerplate.

---

### Loading Entities and Aggregates

Flux Capacitor supports a flexible and powerful approach to loading aggregates and their internal entities using
`FluxCapacitor.loadAggregateFor(...)` and `FluxCapacitor.loadEntity(...)`.

---

#### `loadEntity(entityId)`

Use this method to load a specific entity **without needing to know the aggregate root** it belongs to. This allows APIs
to remain focused and concise—for example:

```java
public record CompleteTask(TaskId taskId) {
}
```

With Flux Capacitor, you can handle this using:

[//]: # (@formatter:off)
```java
FluxCapacitor.loadEntity(taskId).assertAndApply(new CompleteTask(taskId));
```
[//]: # (@formatter:on)

Even if the `Task` is deeply nested within a `Project` or other parent aggregate, this method works because of the
**entity relationship tracking** automatically maintained by Flux Capacitor.

Additional behavior:

- If **multiple entities** match the given ID, the one with the **most recently added relationship** is used.
- The **entire aggregate** containing the entity is loaded, ensuring consistency.
- The returned `Entity<T>` provides methods like `assertAndApply(...)` or `apply(...)`, and includes a reference to the
  enclosing aggregate root.

> This enables true *location transparency* for commands and queries: you don’t need to know or pass along the full
> aggregate path.

#### 🔁 Finding All Aggregates for an Entity

In some scenarios, an entity may be **referenced by multiple aggregates**—for example, when using shared reference
data (e.g. a `Currency`, `Role`, or `Label`). If such an entity is updated, you might want to update *all* aggregates
that reference it.

To retrieve all aggregates that currently include a given entity ID:

```java
Map<String, Class<?>> aggregates = FluxCapacitor.get()
        .aggregateRepository()
        .getAggregatesFor(myEntityId);
```

This returns a map of aggregate IDs and their types.

> ⚠️ **Caution:** This pattern should only be used if you know the number of associated aggregates will remain small and
> bounded. If many aggregates accumulate over time, this lookup can grow unbounded and lead to performance issues.

When used responsibly, this enables patterns like:

[//]: # (@formatter:off)
```java
// Rerender or update every Project referencing a shared Tag
for(Map.Entry<String, Class<?>> entry : FluxCapacitor.get()
                .aggregateRepository().getAggregatesFor(tagId).entrySet()) {
        FluxCapacitor.loadAggregate(entry.getKey(), entry.getValue())
        .apply(new RefreshTag(tagId));
}
```
[//]: # (@formatter:on)

This approach can help keep derived or denormalized data consistent across aggregates.

---

#### `loadAggregateFor(entityId)`

Use this method to retrieve the **aggregate root** that currently contains the specified entity ID.

```java
Entity<MyAggregate> aggregate = FluxCapacitor
        .loadAggregateFor("some-entity-id");
```

Behavior:

- If the ID matches a **child entity**, the enclosing aggregate is returned.
- If the ID refers to an **aggregate root**, that root is returned directly.
- If no aggregate exists, an **empty aggregate** of type `Object` is returned. This enables bootstrapping a new one by
  applying events.

> Use `loadAggregateFor(entityId, Class<T>)` when you need type safety or to avoid relying on inference.

### Alternative Entity Identifiers

Flux Capacitor supports alternative ways to reference an entity using the `@Alias` annotation. This is especially useful
when:

- The entity needs to be looked up using a secondary identifier (e.g. an email address or external ID)
- An entity wants to reference another entity without identifier collisions

#### Lookup via Aliases

Aliases are used when:

- Loading an aggregate or entity using `FluxCapacitor.loadAggregateFor(alias)` or `FluxCapacitor.loadEntity(alias)`.
- Calling `Entity#getEntity(Object id)` on a parent entity.

> If multiple entities share the same alias, behavior is undefined—avoid alias collisions unless intentional.

#### Supported Targets

You can place `@Alias` on:

- **Fields** (e.g., `@Alias String externalId`)
- **Property methods** (e.g., `@Alias String legacyId()`)

If the property is a **collection**, all non-null elements are treated as aliases. If the value is `null` or an empty
collection, it is ignored.

#### Prefix and Postfix

To avoid clashes between IDs in different domains, use the optional `prefix` and `postfix` parameters:

```java

@Alias(prefix = "email:")
String email;

@Alias(postfix = "@external")
String externalId;
```

This ensures that `email@example.com` is stored as `email:email@example.com`, and `12345` becomes `12345@external`.

#### Example

```java

public record UserAccount(@EntityId String userId,
                          @Alias(prefix = "email:") String emailAddress) {

    @Alias
    List<String> oldIds;
}
```

Now the `UserAccount` entity can be looked up using:

```java
Entity<UserAccount> entity = FluxCapacitor
        .loadEntity("email:foo@example.com");
```

or

```java
Entity<UserAccount> entity = FluxCapacitor
        .loadEntity("1234"); // one of the oldIds
```

---

### 💡 Tip: Use `@Alias` on Strongly-Typed `Id<T>` Identifiers

While `@Alias` can be applied to any field or property, it's often more convenient and robust to use it on a
strongly-typed identifier that extends `Id<T>`:

- `Id<T>` supports prefixing, case-insensitive matching, and type-safe deserialization.
- The `@Alias` annotation recognizes the repository ID computed by the `Id<T>` implementation.
- You don’t need to repeat the prefix in `@Alias`—it's already encoded in the `Id`.

```java
public class Email extends Id<UserAccount> {
    public Email(String email) {
        super(email, "email:");
    }
}

@Alias
Email email;
```

This allows you to load the entity by its alias:

```java
Entity<UserAccount> account = FluxCapacitor
        .loadEntity(new Email("john@example.com"));
```

This makes aliasing more explicit and reusable—particularly useful in larger applications.

### Routing Behavior

Flux automatically routes child-targeted updates like `AuthorizeUser` and `RevokeAuthorization` to the correct nested
entity using the `@EntityId`. You don’t need to write custom matching logic — the routing works transparently as long
as:

- The root aggregate is loaded (e.g. using `loadAggregate(userId)`), and
- The update contains enough identifying information to locate the nested entity

### Summary

This model leads to extremely clean domain logic:

- No need to manipulate collections in the aggregate
- No need for boilerplate logic to find, update, or remove children
- Nested updates stay localized to the child entity itself

---

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

---

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

---

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
public record UserAccount(@EntityId UserId userId,
                          UserProfile profile,
                          boolean accountClosed) {
}
```

By default, the collection name is derived from the class’s **simple name** (UserAccount → `"UserAccount"`),
unless explicitly overridden via an annotation like `@Aggregate`, `@Stateful` or `@Searchable` or in the search/index
call:

```java
@Aggregate(searchable = true, collection = "users", timestampPath = "profile/createdAt")
```

---

### Querying Indexed Documents

Use the fluent `search(...)` API:

```java
List<UserAccount> admins = FluxCapacitor
        .search("users")
        .match("admin", "profile/role")
        .inLast(Duration.ofDays(30))
        .sortBy("profile/lastLogin", true)
        .fetch(100);
```

You can also query by class:

```java
List<UserAccount> users = FluxCapacitor
        .search(UserAccount.class)
        .match("Netherlands", "profile.country")
        .fetchAll();
```

> **Note:** You can choose to split path segments using either a dot (`.`) or a slash (`/`).  
> For example, `profile.name` and `profile/name` are treated identically.  
> This flexibility can be useful when working with tools or serializers that prefer one style over the other.

---

### Common Filtering Constraints

Flux supports a rich set of constraints:

- `lookAhead("cat", paths...)` – search-as-you-type lookups
- `query("*text & (cat* | hat)", paths...)` – full-text search
- `match(value, path)` – field match
- `matchFacet(name, value)` – match field with `@Facet`
- `between(min, max, path)` – numeric or time ranges
- `since(...)`, `before(...)`, `inLast(...)` – temporal filters
- `anyExist(...)` – match if *any* of the fields are present
- Logical operations: `not(...)`, `all(...)`, `any(...)`

Example:

[//]: # (@formatter:off)
```java
FluxCapacitor.search("payments")
    .match("FAILED", "status")
    .inLast(Duration.ofDays(1))
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

> 💡 **Tip:** Use `@Facet` on frequently-filtered fields (e.g. `status`, `type`, `category`) to take full advantage
> of this optimization.

---

### Facet Statistics

When a field or getter is annotated with `@Facet`, you can also retrieve **facet statistics** — e.g., how many documents
exist per value of a given property. This is useful for building **filters with counts**, such as product categories,
user roles, or status indicators.

#### Example: Product Breakdown by Category and Brand

Suppose you have the following model:

```java

@Searchable
public record Product(@Facet String category,
                      @Facet String brand,
                      String name,
                      BigDecimal price) {
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
  { "name": "category", "value": "headphones", "count": 55 },
  { "name": "brand", "value": "Acme", "count": 45 },
  { "name": "brand", "value": "NoName", "count": 10 }
]
```
[//]: # (@formatter:on)

> 💡 **Tip:** Use `matchFacet("category", "headphones")` to filter efficiently by facet value. This is generally
> faster than `match(...)`.

Each `FacetStats` object will contain:

- the facet name (e.g., `category`)
- the distinct values (e.g., `"electronics"`, `"clothing"`)
- the number of documents per value

---

### Search Index exclusion

By default, all non-transient properties of a document are included in the search index. However, you can fine-tune what
fields get indexed using the following annotations:

#### `@SearchExclude`

Use `@SearchExclude` to exclude a field or type from search indexing. This prevents the property from being matched in
search queries, though it will still be present in the stored document and accessible at runtime.

```java

public record Order(String id,
                    Customer customer,
                    @SearchExclude byte[] encryptedPayload) {
}
```

You can also exclude entire types:

```java

@SearchExclude
public record EncryptedData(byte[] value) {
}
```

In this case, none of the properties of `EncryptedData` will be indexed, unless you override selectively with
`@SearchInclude`.

---

#### `@SearchInclude`

Use `@SearchInclude` to **override** an exclusion. This is functionally equivalent to `@SearchExclude(false)` and is
typically used on a field or class that would otherwise be excluded by inheritance or parent-level settings.

```java

@SearchExclude
public record BaseDocument(String internalNotes,
                           @SearchInclude String publicSummary) {
}
```

Here, `internalNotes` will not be indexed, but `publicSummary` will be.

---

### Behavior Summary

| Annotation                                  | Effect                                                           |
|---------------------------------------------|------------------------------------------------------------------|
| `@SearchExclude`                            | Prevents property/type from being indexed for search             |
| `@SearchExclude(false)` or `@SearchInclude` | Explicitly includes a property even if a parent type is excluded |
| *No annotation*                             | Field is included in the search index by default                 |

> **Note:** If you want a field to be completely omitted from storage (not just indexing), mark it as `transient`, use
`@JsonIgnore`, or another serialization-related annotation.

---

### Rapid Sorting and Filtering

To enable efficient **range filters** and **sorted results** in document searches, annotate properties with `@Sortable`:

```java

public record Product(@Sortable BigDecimal price,
                      @Sortable("releaseDate") Instant publishedAt) {
}
```

This tells Flux Capacitor to **pre-index** these fields in a lexicographically sortable format. When you issue a search
with a `between()` constraint or `.sort(...)` clause, the Flux Platform can evaluate it **directly in the data store** —
without needing to load and compare documents in memory.

#### 🚀 Optimized Search Example

```java
List<Product> results = FluxCapacitor.search(Product.class)
        .between("price", 10, 100)
        .sort("releaseDate")
        .fetch(100);
```

This performs a fast, index-backed range query across all products priced between €10 and €100, sorted by their
`releaseDate`.

> ⚠️ You can always sort by any field — even if it's not `@Sortable` — but performance will degrade because the sorting
> happens **after** all matching documents are loaded.

#### ⚙️ What Gets Indexed?

Flux Capacitor normalizes and encodes sortable fields depending on their value type:

| Type           | Behavior                                                                 |
|----------------|--------------------------------------------------------------------------|
| Numbers        | Padded base-10 string (preserves order, supports negatives)              |
| Instants       | ISO-8601 timestamp format                                                |
| Strings/Others | Normalized (lowercased, trimmed, diacritics removed)                     |

This ensures that sorting is **consistent** and **correct** across types and locales.

#### 🧠 Nested and Composite Values

If the sortable field is:

- A **collection** → Max element is indexed. Create a getter if you need sorting on the min element
- A **map** → Values are indexed using `key/propertyName` path
- A **nested object** annotated with `@Sortable` → Its `toString()` is used
- A **POJO with `@Sortable` fields** → Those nested values are indexed with prefixed paths

#### ⚠️ Important Notes

- **No retroactive indexing**: Adding `@Sortable` to a field does **not** automatically reindex existing documents.
- To apply sorting retroactively, trigger a reindex (e.g. with `@HandleDocument` and a bumped `@Revision`)
- Sorting and filtering still happen **within the Flux Platform**, but *without* `@Sortable` the logic falls back
  to **in-memory evaluation** — which is much slower.

> ✅ Use `@Sortable` together with `@Facet` if you want both sorting and aggregation/filtering on a field.

---

### Customizing Returned Fields

When performing a search, you can control which fields are included or excluded in the returned documents.

This is useful for:

- Hiding sensitive fields (e.g. private data, tokens)
- Reducing payload size
- Optimizing performance when only partial data is needed

#### Example

Given the following indexed document:

```json
{
  "id": "user123",
  "profile": {
    "name": "Alice",
    "email": "alice@example.com",
    "ssn": "123456789"
  },
  "roles": [
    "user",
    "admin"
  ]
}
```

You can exclude sensitive fields like so:

[//]: # (@formatter:off)
```java
FluxCapacitor.search("users")
     .exclude("profile.ssn")
     .fetch(50);
```
[//]: # (@formatter:on)

This will return:

```json
{
  "id": "user123",
  "profile": {
    "name": "Alice",
    "email": "alice@example.com"
  },
  "roles": [
    "user",
    "admin"
  ]
}
```

> You can also use `.includeOnly(...)` instead to explicitly whitelist fields.
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

---

## Tracking and Updating Documents

Flux Capacitor allows you to **track changes to your document store** using the `@HandleDocument` annotation.  
This enables handlers to react to document updates in real time — much like handling events.

```java

@HandleDocument
void handle(UserAccount user) {
    log.info("UserAccount {} was added or updated", user.getUserId());
}
```

### How It Works

Every time a document is (re)indexed, it receives a new message index (based on the timestamp of the update).  
Handlers annotated with `@HandleDocument` will observe these updates as they pass through the **document log**.

> ⚠️ When catching up from behind (e.g. in a replay), only the **latest version** of a document is retained per key.
> Earlier intermediate versions are not visible to late consumers.

This makes `@HandleDocument` ideal for **live processing**, **projecting the latest known state**, or
**cache rebuilds**.

---

### Transforming Stored Documents

`@HandleDocument` can also be used to **update documents in place**.

If the handler returns a **newer revision** of the document, Flux will reindex and persist the result:

```java

@HandleDocument("users")
UserAccount upgrade(UserAccount oldUser) {
    return new UserAccount(oldUser.getId(), normalizeEmail(oldUser.getEmail()));
}
```

The returned value replaces the previous document (same ID), **only if** it has a **higher `@Revision`**.

> ✅ This creates a powerful and durable upgrade path — ideal for data normalization, filling missing fields, or
> applying business logic retroactively.

To **delete** a document from the store, return `null`:

```java

@HandleDocument("users")
UserAccount upgrade(UserAccount user) {
    return user.isTestUser() ? null : user;
}
```

> Transformations are persisted reliably — even across application restarts — making this a great way to evolve your
> document model safely.

---

### Use Cases

- Auto-upcasting legacy documents
- Filling or correcting derived fields
- Cleaning up invalid or deprecated data
- Running background migrations or rehydration jobs
- Real-time analytics and change logging

---

### Handler Configuration

You can subscribe to a document collection using any of the following styles:

- `@HandleDocument(documentClass = MyModel.class)` — resolves the collection via the model’s `@Searchable` annotation
- `@HandleDocument("myCollection")` — binds directly to the named collection
- `@HandleDocument` — infers the collection from the **first parameter** of the handler method

---

### Replay for Full Collection Migration

Want to upgrade all existing documents in a collection? Combine `@HandleDocument` with replays:

1. Create a handler method that returns upgraded documents
2. Increment the `@Revision` on the model class
3. Attach a custom consumer with `@Consumer(minIndex = 0, ...)`
4. Deploy temporarily until migration completes

This is a robust and rapid way to **reindex, clean, or refactor your stored documents** in-place.

```java

@Consumer(name = "reindex-users", minIndex = 0)
class ReindexUsers {
    @HandleDocument("users")
    UserAccount upgrade(UserAccount legacyUser) {
        return fixLegacyState(legacyUser);
    }
}
```

Once the transformation is complete, the handler can be safely removed.

---

## Protecting Sensitive Data

Flux Capacitor offers built-in support for handling sensitive information with care using the `@ProtectData` and
`@DropProtectedData` annotations.

These tools help prevent sensitive fields (e.g., passwords, SSNs, tokens) from being unnecessarily stored, logged, or
retained—supporting compliance with data protection standards like **GDPR** and **ISO 27001**.

---

### `@ProtectData`: Mark Sensitive Fields

To prevent a field from being stored with the rest of a message payload, annotate it with `@ProtectData`:

```java

public record RegisterCitizen(String name,
                              @ProtectData String socialSecurityNumber) {
}
```

When this message is dispatched, the `socialSecurityNumber` will be:

- **Offloaded** to a separate data vault
- **Redacted** from the main payload (not visible in logs or message inspectors)
- **Re-injected** automatically when the message is handled

This happens transparently—you can access the field as usual in handler methods.

---

### `@DropProtectedData`: Remove When Done

If the protected data should only be retained **temporarily**, annotate the handler method with `@DropProtectedData`:

```java

@HandleCommand
@DropProtectedData
void handle(RegisterCitizen command) {
    validate(command.getSocialSecurityNumber());
    ...
}
```

Once this handler completes:

- The injected `socialSecurityNumber` is **permanently deleted** from storage.
- Future replays will deserialize the message with that field **omitted**.

> ⚠️ This mechanism ensures sensitive fields are **usable just-in-time** and **discarded thereafter**.

---

### Use Cases

- Temporary use of sensitive tokens or credentials
- Compliance with **data minimization** and **retention policies**
- Preventing accidental exposure in logs or audits

---

### Replays and Protected Data

During replays, if a message with protected fields is re-invoked **after** data has been dropped, those fields will be
missing or set to `null`. Design your handlers to tolerate this by making such fields optional or guarding usage.

---

### Notes

- Only **fields** can be annotated with `@ProtectData` (not method parameters).
- Dropping data is **irreversible**. Make sure all processing is complete before it is removed.
- Support for nested structures is currently limited — annotate each sensitive field explicitly.

---

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
public record CreateUser(String userId) { // `userId` renamed from `id`
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

Upcasters can also **drop** a message by returning `null` or `void`, or **split** it into multiple new ones:

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

This works for **any** stored data — not just messages, but also snapshots, key-value entries, and documents.

---

### Downcasting

Downcasting does the reverse: it converts a newer object into an older format. This is useful for emitting
**legacy-compatible** data or supporting **external systems**.

```java
class CreateUserDowncaster {

    @Downcast(type = "com.example.CreateUser", revision = 1)
    ObjectNode downcastV1toV0(ObjectNode json) {
        json.set("id", json.remove("userId"));
        return json;
    }
}
```

To downcast an object to a desired revision, use:

```java
FluxCapacitor.downcast(object, revision);
```

> This returns the downcasted version of the object in serialized form (e.g., as an `ObjectNode`), allowing you to
> inspect or persist the object as it would appear in an earlier revision.

---

### Registration

When using Spring, any bean containing `@Upcast` or `@Downcast` methods is **automatically registered** with the
serializer.

Outside of Spring, register them manually:

[//]: # (@formatter:off)
```java
serializer.registerCasters(new CreateUserUpcaster(), new CreateUserDowncaster());
```
[//]: # (@formatter:on)

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

---

## Filtering Object Content

Flux Capacitor provides a flexible way to **redact or tailor object content per user** using the `@FilterContent`
annotation.

This enables domain models or documents to define exactly what is visible to different users, based on roles, ownership,
or context.

### Basic Example

```java

@FilterContent
Order filter(User user) {
    return user.hasRole(Role.admin) ? this : new Order(maskSensitiveFieldsOnly());
}
```

To invoke filtering:

[//]: # (@formatter:off)
```java
Order filtered = FluxCapacitor.filterContent(order, currentUser);
```
[//]: # (@formatter:on)

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
LineItem filter(User user, Order root) {
    return root.isOwner(user) ? this : null;
}
```

### Key Behaviors

- `@FilterContent` applies only when called via `FluxCapacitor.filterContent(...)` or `Serializer.filterContent(...)`
- **It is not automatic** — for performance reasons, content filtering is not applied implicitly (e.g. during search or
  document deserialization)
- If no method is annotated with `@FilterContent`, the object is returned unmodified

---

## Kotlin Support

Flux Capacitor provides full support for Kotlin, including:

- Use of `record`-like [data classes](https://kotlinlang.org/docs/data-classes.html),
- Optional types (e.g., `String?`) instead of `@Nullable`,
- Seamless use of Kotlin-specific language features (e.g., `when`, `sealed class`, `companion object`),
- Support for `Class<T>` parameters passed as `MyClass::class` or `MyClass::class.java`,
- Automatic recognition of Kotlin modules during deserialization (see below).

### Automatic Jackson Integration

Flux Capacitor includes [Jackson Kotlin Module](https://github.com/FasterXML/jackson-module-kotlin) integration when
available on the classpath. You do **not** need to manually register the module or use a service loader.
If the `jackson-module-kotlin` dependency is present, it will be loaded dynamically for JSON (de)serialization.

> 💡 If the module is missing, Flux Capacitor will fall back gracefully to standard Jackson behavior — no errors or
> warnings.

This enables correct serialization and deserialization of Kotlin constructs like:

- default parameters,
- `val` / `var` fields,
- nullability,
- `data class` equality and hashing,
- and Kotlin-style constructor parameter mapping.

> **Note:** Flux Capacitor does *not* require a Kotlin dependency itself. Kotlin support is purely optional and works
> automatically when Kotlin and its Jackson module are used in your application.

---

## Configuring Application Properties

Flux Capacitor provides a static utility, `ApplicationProperties`, for resolving configuration values across
environments, tests, and production. It supports:

- Layered resolution from environment variables, system properties, and `.properties` files
- Placeholder substitution (e.g. `${my.env}`)
- Encrypted values with automatic decryption
- Typed access: `getBooleanProperty`, `getIntegerProperty`, etc.

### Property Resolution Order

Properties are resolved in the following order of precedence:

1. `EnvironmentVariablesSource` – e.g. `export MY_SETTING=value`
2. `SystemPropertiesSource` – e.g. `-Dmy.setting=value`
3. `ApplicationEnvironmentPropertiesSource` – e.g. `application-dev.properties`
4. `ApplicationPropertiesSource` – base fallback (`application.properties`)
5. *(Optional)*: Spring’s `Environment` is added as a fallback source if Spring is active

To specify the environment (`dev`, `prod`, etc.), define:

```bash
export ENVIRONMENT=dev
```

This allows `application-dev.properties` to override base properties.

### Example Usage

```java
String name = ApplicationProperties.getProperty("app.name", "DefaultApp");
boolean enabled = ApplicationProperties.getBooleanProperty("feature.toggle", true);
int maxItems = ApplicationProperties.getIntegerProperty("limit.items", 100);
```

### Encrypted Values

Flux Capacitor supports secure storage of secrets using its built-in encryption utility. To use encryption:

1. **Generate a new key** with:

   [//]: # (@formatter:off)
    ```java
    String key = DefaultEncryption.generateNewEncryptionKey();
    System.out.println(key);
    // => ChaCha20|KJh832h1f7shDFb...  -> Save and use as ENCRYPTION_KEY
    ```
    [//]: # (@formatter:on)

2. **Set the encryption key** via an environment variable or system property:

    ```bash
    export ENCRYPTION_KEY=ChaCha20|KJh832h1f7shDFb...
    ```

3. **Encrypt values** at build/deploy time:

   [//]: # (@formatter:off)
    ```java
    String encrypted = ApplicationProperties.encryptValue("secret-google-key");
    System.out.println(encrypted);
    // => encrypted|ChaCha20|mm8yeY8TXtNpdrwO:REdej56zvFXc:b7oQdmnpQpUzagKtma9JLQ==
    ```
    [//]: # (@formatter:on)

4. **Add encrypted values to your config**:

    ```properties
    google.apikey=encrypted|ChaCha20|mm8yeY8TXtNpdrwO:REdej56zvFXc:b7oQdmnpQpUzagKtma9JLQ==
    ```

5. **Resolve them normally in code**:

   [//]: # (@formatter:off)
    ```java
    String apiKey = ApplicationProperties.getProperty("google.apikey");
    // -> "secret-google-key"
    ```
    [//]: # (@formatter:on)

Decryption is transparent. Flux detects encrypted values and decrypts them automatically.

### In Tests

Properties can be defined in your `test/resources/application.properties` or overridden via system properties.

```bash
-Dmy.test.override=true
```

Or dynamically inject mock values:

[//]: # (@formatter:off)
```java
TestFixture.create(MyHandler.class)
    .withProperty("my.test.value", "stub")
    .whenCommand("/users/create-job.json")
    .expectSchedules(ScheduledJob.class);
```
[//]: # (@formatter:on)

> This is especially useful in integration or fixture-based tests.

---

## Parameter Injection with Custom Resolvers

Flux Capacitor allows fine-grained control over **handler method parameters** using the `ParameterResolver` interface.  
This lets you inject **any value** into annotated handler methods — beyond just payload, metadata, etc.

### How It Works

When a message is dispatched to a handler (e.g. via `@HandleEvent`, `@HandleCommand`, etc.), the framework scans the
method’s parameters and tries to resolve each one using the configured `ParameterResolvers`.

By default, Flux Capacitor supports injection of the following parameters into handler methods:

- The **message payload** (automatically matched by type)
- The full **`Message`**, **`Schedule`**, or **`WebRequest`**
- The raw **`DeserializingMessage`** (for low-level access)
- The message **`Metadata`**
- The currently authenticated **`User`** (if available)
- The associated **`Entity`** wrapper or the entity value itself
- The **triggering message** (annotated with `@Trigger`)
- Any **Spring bean** (when Spring integration is enabled)

Other contextual values like **message ID**, **timestamp**, etc. can be obtained from the `Message`.

### Example: Default Resolution

```java

@HandleEvent
void handle(CreateUser event, Metadata metadata, SerializedMessage message) {
    log.info("User created at {}", Instant.ofEpochMilli(message.getTimestamp()));
}
```

### Writing Your Own Parameter Resolver

You can register a custom parameter resolver to inject arbitrary values, such as headers, timestamps, or contextual
objects:

```java
public class TimestampParameterResolver implements ParameterResolver<DeserializingMessage> {
    @Override
    public Function<DeserializingMessage, Object> resolve(Parameter parameter, Annotation methodAnnotation) {
        if (parameter.getType().equals(Instant.class)) {
            return DeserializingMessage::getTimestamp;
        }
        return null;
    }
}
```

Then register it via your builder:

[//]: # (@formatter:off)
```java
DefaultFluxCapacitor.builder()
    .addParameterResolver(new TimestampParameterResolver())
    .build();
```
[//]: # (@formatter:on)

And use it in your handler:

```java

@HandleCommand
void handle(CreateOrder command, Instant timestamp) {
    log.info("Command received at {}", timestamp);
}
```

### Use Cases

- Injecting request-specific context (e.g. tracing info)
- Supporting custom annotations (e.g. `@FromHeader`)
- Enabling access to correlated data (e.g. parent entity)
- Binding to environment or system values

---

Custom parameter injection is a powerful tool for modular, contextual logic. It works seamlessly with all handler
annotations (`@HandleEvent`, `@HandleCommand`, `@HandleQuery`, `@HandleError`, etc.) and opens up a clean way to avoid
boilerplate argument passing.

---

## Interceptors: Dispatching, Handling, and Batching

Flux Capacitor offers a flexible and extensible **interceptor model** to hook into key stages of the message lifecycle:

| Interceptor Type          | Target Phase                         | Typical Use Cases                                      |
|---------------------------|--------------------------------------|--------------------------------------------------------|
| `DispatchInterceptor`     | Before publishing/handling a message | Mutate, block, enrich, or observe outgoing messages    |
| `HandlerInterceptor`      | Around handler execution             | Validation, logging, authentication, result decoration |
| `BatchInterceptor`        | Around batch processing              | Tracing, retries, context injection, metrics           |
| `MappingBatchInterceptor` | Batch transformation                 | Filtering or rewriting entire message batches          |

All interceptors are **pluggable**, and can be configured via:

- `FluxCapacitorBuilder` for global registration
- `@Consumer(handlerInterceptors = ...)`
- `@Consumer(batchInterceptors = ...)`

---

### DispatchInterceptor

A `DispatchInterceptor` hooks into the **message dispatch phase**—just before the message is published to Flux or
handled locally.

```java
public class LoggingInterceptor implements DispatchInterceptor {
    @Override
    public Message interceptDispatch(Message message, MessageType type, String topic) {
        log.info("Dispatching: {} to topic {}", type, topic);
        return message;
    }
}
```

**Capabilities:**

- `interceptDispatch(...)`: Modify, block, or inspect outgoing messages.
- `modifySerializedMessage(...)`: Mutate message after serialization but before transmission.
- `monitorDispatch(...)`: Observe the final message as it's sent.

**Register globally:**

[//]: # (@formatter:off)
```java
FluxCapacitorBuilder.builder()
    .addDispatchInterceptor(new LoggingInterceptor(),MessageType.COMMAND,MessageType.EVENT);
```
[//]: # (@formatter:on)

---

### HandlerInterceptor

A `HandlerInterceptor` allows wrapping the execution of handler methods, ideal for:

- **Authorization & Access Control** — prevent unauthorized commands or queries based on the current user
- **Auditing and Logging** — log incoming messages, handler invocations, or emitted results
- **Validation Hooks** — perform extra validation before or after handler execution
- **Result Transformation** — enrich or reformat results before they’re published or returned
- **Thread Context Propagation** — populate thread-local state like correlation IDs or security principals

```java
public class AuthorizationInterceptor implements HandlerInterceptor {
    @Override
    public Function<DeserializingMessage, Object> interceptHandling(
            Function<DeserializingMessage, Object> next, HandlerInvoker invoker) {
        return message -> {
            if (!isAuthorized(message)) {
                throw new UnauthorizedException();
            }
            return next.apply(message);
        };
    }
}
```

**Register via annotation or builder:**

[//]: # (@formatter:off)
```java
@Consumer(handlerInterceptors = AuthorizationInterceptor.class)
public class SecureCommandHandler { ... }

FluxCapacitorBuilder.builder()
    .addHandlerInterceptor(new AuthorizationInterceptor(), true, MessageType.COMMAND);
```
[//]: # (@formatter:on)

Here, `true` for `highPriority` means your interceptor runs **before** other interceptors.

---

### BatchInterceptor

Wraps around the processing of a **full message batch** by a single consumer, ideal for:

- Structured logging
- Performance instrumentation
- Scoped resources (e.g. transactions)

```java
public class LoggingBatchInterceptor implements BatchInterceptor {
    @Override
    public Consumer<MessageBatch> intercept(Consumer<MessageBatch> consumer, Tracker tracker) {
        return batch -> {
            log.info("Start processing {} messages", batch.size());
            consumer.accept(batch);
        };
    }
}
```

📌 **Global install:**

[//]: # (@formatter:off)
```java
FluxCapacitorBuilder.builder()
    .addBatchInterceptor(new LoggingBatchInterceptor(),MessageType.EVENT);
```
[//]: # (@formatter:on)

---

### MappingBatchInterceptor

This specialization of `BatchInterceptor` can rewrite or filter the batch itself:

```java
MappingBatchInterceptor filterTestMessages = (batch, tracker) -> {
    var filtered = batch.getMessages().stream()
            .filter(m -> !m.getMetadata().containsKey("testOnly"))
            .toList();
    return batch.withMessages(filtered);
};
```

📌 Install it globally:

[//]: # (@formatter:off)
```java
FluxCapacitorBuilder.builder()
    .addBatchInterceptor(filterTestMessages, MessageType.QUERY);
```
[//]: # (@formatter:on)

---

Interceptors are a central way to add cross-cutting behavior across all stages of message flow, from dispatch to
handling and batching—empowering modular, observable, and policy-driven systems.

---

## Configuring Flux Capacitor

The `FluxCapacitorBuilder` interface is the primary entry point for configuring a `FluxCapacitor` instance. It allows
fine-grained customization of all core behaviors, including message consumers, dispatch logic, interceptors, caching,
serialization, metrics, and much more.

Most applications use the default builder via:

```java
FluxCapacitorBuilder builder = DefaultFluxCapacitor.builder();
```

In Spring environments, it can be customized by implementing the `FluxCapacitorCustomizer` interface:

```java

@Component
public class MyCustomizer implements FluxCapacitorCustomizer {
    @Override
    public FluxCapacitorBuilder customize(FluxCapacitorBuilder builder) {
        return builder.addParameterResolver(new CustomResolver());
    }
}
```

---

### Key Capabilities

#### Consumer and Tracking Configuration

- `configureDefaultConsumer(MessageType, UnaryOperator<ConsumerConfiguration>)` to adjust the default consumer behavior
  per message type.
- `addConsumerConfiguration(...)` to register additional consumers for selected message types.
- `forwardWebRequestsToLocalServer(...)` to redirect incoming `@HandleWeb` calls to an existing local HTTP
  server.

#### Interceptors and Decorators

- `addHandlerInterceptor(...)`, `addBatchInterceptor(...)`, and `addDispatchInterceptor(...)` to apply interceptors by
  message type.
- Interceptors may be prioritized (`highPriority = true`) or restricted to specific message types.
- `replaceMessageRoutingInterceptor(...)` overrides the routing logic for outbound messages.
- `addHandlerDecorator(...)` adds more generic handler-level logic.

#### Data, Identity, and Correlation

- `replaceIdentityProvider(...)` to control ID generation for messages or functional identifiers.
- `replaceCorrelationDataProvider(...)` to define how correlation metadata is attached to outbound messages.
- `registerUserProvider(...)` to integrate custom user authentication and inject `User` into handlers.

#### Caching and Snapshotting

- `replaceCache(...)` and `withAggregateCache(...)` to plug in custom caching backends.
- `replaceRelationshipsCache(...)` for customizing the cache used in association-based message routing.
- `replaceSnapshotSerializer(...)` if you want to store snapshots differently from events.

#### Message Serialization

- `replaceSerializer(...)` changes the default JSON serializer (e.g., for Jackson customizations).
- `replaceDocumentSerializer(...)` lets you influence how document fields are indexed and stored for search.

#### Parameter Injection and Handler Behavior

- `addParameterResolver(...)` registers a `ParameterResolver` to inject custom arguments into handler methods.
- `replaceDefaultResponseMapper(...)` and `replaceWebResponseMapper(...)` to change how handler return values are mapped
  into responses.

#### Application Configuration

- `addPropertySource(...)` and `replacePropertySource(...)` control the configuration hierarchy (e.g., ENV > system
  props > application.properties).
- Integrates with `ApplicationProperties` for encrypted or templated config values.

#### Task Scheduling and Execution

- `replaceTaskScheduler(...)` to inject a custom scheduler for async or delayed task execution.

---

### ❌ Optional Behavior Toggles

These methods disable internal features as needed:

| Method                               | Disables                                                           |
|--------------------------------------|--------------------------------------------------------------------|
| `disableErrorReporting()`            | Suppresses error publishing to `ErrorGateway`                      |
| `disableShutdownHook()`              | Prevents the JVM shutdown hook                                     |
| `disableMessageCorrelation()`        | Skips automatic correlation ID injection                           |
| `disablePayloadValidation()`         | Turns off payload type validation                                  |
| `disableDataProtection()`            | Disables `@ProtectData` and `@DropProtectedData` filtering         |
| `disableAutomaticAggregateCaching()` | Skips aggregate cache setup                                        |
| `disableScheduledCommandHandler()`   | Removes default handler for scheduled commands                     |
| `disableTrackingMetrics()`           | Prevents emitting metrics during message tracking                  |
| `disableCacheEvictionMetrics()`      | Disables cache eviction telemetry                                  |
| `disableWebResponseCompression()`    | Prevents gzip compression for web responses                        |
| `disableAdhocDispatchInterceptor()`  | Disallows use of `AdhocDispatchInterceptor.runWith...()` utilities |

---

### Final Assembly

Once the builder is configured, construct the `FluxCapacitor` instance by passing in a `Client` (usually a
`WebSocketClient` or `LocalClient`):

[//]: # (@formatter:off)
```java
FluxCapacitor flux = builder.build(myClient);
```
[//]: # (@formatter:on)

To mark it as the global application-wide instance (i.e., accessible via `FluxCapacitor.get()`):

[//]: # (@formatter:off)
```java
builder.makeApplicationInstance(true).build(myClient);
```
[//]: # (@formatter:on)

This is the central instance that orchestrates message gateways, tracking, scheduling, and storage across your
application.  
If Spring is used, the application instance is automatically set by Spring and unset when the Spring context is closed.

---

### Spring Auto-Configuration

Flux Capacitor integrates well with Spring. If you're using Spring (or Spring Boot), many components are auto-configured
for you:

- If you provide a bean of type `Serializer`, `Cache`, `Client`, `UserProvider`, or `WebResponseMapper`, it will be
  automatically picked up by the builder.
- `Upcasters` and `Downcasters` are auto-registered if detected on Spring beans.
- Handlers (`@Handle...`) are automatically registered after the context is refreshed.
- `@TrackSelf`, `@Stateful`, and `@SocketEndpoint` beans are auto-detected and wired via post-processors.
- If no `Client` is configured explicitly, Flux Capacitor tries to create a `WebSocketClient` (based on available
  properties), or falls back to a `LocalClient`.

> ⚠️ If you're using **Spring without Spring Boot**, be sure to add `@Import(FluxCapacitorSpringConfig.class)` to enable
> auto-configuration.

You can always override or customize behavior via a `FluxCapacitorCustomizer`.

#### Injectable Beans

Flux Capacitor exposes several core components as Spring beans, making them easy to inject into your application:

| Bean Type              | Purpose                                                       |
|------------------------|---------------------------------------------------------------|
| `FluxCapacitor`        | Access to the full runtime and configuration                  |
| `CommandGateway`       | Dispatch commands and receive results                         |
| `EventGateway`         | Publish events to the global log                              |
| `QueryGateway`         | Send queries and await answers                                |
| `MetricsGateway`       | Emit custom metrics messages                                  |
| `ErrorGateway`         | Report errors manually                                        |
| `ResultGateway`        | Manually publish results from asynchronous flows              |
| `MessageScheduler`     | Schedule commands or other messages in the future             |
| `AggregateRepository`  | Load and store aggregates                                     |
| `DocumentStore`        | Search, filter, and persist document models                   |
| `KeyValueStore`        | Access key-value persisted state                              |

You can simply inject any of these into your Spring-managed components:

```java

@Component
@AllArgsConstructor
public class MyService {
    private final CommandGateway commandGateway;

    public void doSomething() {
        commandGateway.sendAndForget(new MyCommand(...));
    }
}
```

> ⚠️ **Note:** While dependency injection is supported, it is **not the recommended approach** in most cases.

Instead, prefer using the static methods on the `FluxCapacitor` class:

[//]: # (@formatter:off)
 ```java
 FluxCapacitor.sendCommand(new MyCommand(...));
 FluxCapacitor.query(new GetUserProfile(userId));
 FluxCapacitor.publishEvent(new UserLoggedIn(...));
 ```
[//]: # (@formatter:on)

This avoids boilerplate, reduces coupling to Spring, and works equally well in non-Spring contexts like tests or
lightweight setups.

---

## WebSocketClient: Connect to the Flux Platform

The `WebSocketClient` is the default client used to connect to the Flux Platform over WebSocket. It provides full access
to the event store, message gateways, tracking, search, scheduling, and key-value storage subsystems via configurable,
high-throughput sessions.

### Creating a WebSocketClient

To configure and instantiate a WebSocket-backed client:

```java
WebSocketClient client = WebSocketClient.newInstance(
        WebSocketClient.ClientConfig.builder()
                .serviceBaseUrl("wss://my.flux.host")
                .name("my-service")
                .build());

FluxCapacitor flux = FluxCapacitor.builder().build(client);
```

This is the most common setup for production and shared environments. It connects to a remote Flux runtime via the
service base URL, which must point to the desired deployment.

---

### Client Configuration (`ClientConfig`)

The `ClientConfig` class defines all connection, routing, compression, and tracking parameters. It is fully immutable
and can be created or extended using the `toBuilder()` pattern.

Key options include:

| Setting                     | Description                                                  | Default                          |
|-----------------------------|--------------------------------------------------------------|----------------------------------|
| `serviceBaseUrl`            | Base URL for all subsystems <br/>(e.g. `wss://my.flux.host`) | `FLUX_BASE_URL` property         |
| `name`                      | Name of the application                                      | `FLUX_APPLICATION_NAME` property |
| `applicationId`             | Optional app ID                                              | `FLUX_APPLICATION_ID` property   |
| `id`                        | Unique client instance ID                                    | `FLUX_TASK_ID` property or UUID  |
| `compression`               | Compression algorithm                                        | `LZ4`                            |
| `pingDelay` / `pingTimeout` | Heartbeat intervals for WebSocket health                     | 10s / 5s                         |
| `disableMetrics`            | Whether to suppress all outgoing metrics                     | `false`                          |
| `typeFilter`                | Optional message type restriction                            | `null`                           |

---

### Subsystem Sessions

Flux opens multiple WebSocket sessions to handle parallel workloads. You can tune the number of sessions per subsystem:

```java
ClientConfig config = ClientConfig.builder()
        .eventSourcingSessions(2)
        .searchSessions(3)
        .gatewaySessions(Map.of(COMMAND, 2, EVENT, 1))
        .build();
```

Each session can multiplex multiple consumers or producers under the hood. Use more sessions to improve parallelism and
isolation across critical workloads.

---

### Tracking Configuration

Tracking clients can use local caches to optimize polling performance when many consumers are tracking the same topic or
message type.

```java
ClientConfig config = ClientConfig.builder()
        .trackingConfigs(Map.of(
                EVENT, TrackingClientConfig.builder()
                        .sessions(2)
                        .cacheSize(1000)
                        .build()))
        .build();
```

The `cacheSize` determines how many messages are buffered in-memory per topic. This helps reduce round-trips to the
platform and can significantly boost performance in high-fanout projections or handlers.

---

### Integration with FluxCapacitorBuilder

Once created, the client is passed into the builder:

```java
FluxCapacitor flux = FluxCapacitor.builder()
        .makeApplicationInstance(true)
        .build(webSocketClient);
```

> ℹ️ Use `makeApplicationInstance(true)` to install the Flux instance as a global singleton (`FluxCapacitor.get()`).
> Default **true** in Spring setups.

---

### Local Alternative

For testing or lightweight local development, use the in-memory `LocalClient` instead:

```java
FluxCapacitor flux = FluxCapacitor.builder().build(new LocalClient());
```

This simulates the entire platform in-memory without external dependencies.

--- 

## Compatibility and Dependencies

### Java Version

Flux Capacitor requires **JDK 21 or higher** to compile and run. It is actively tested on **JDK 24** and remains
compatible with recent versions.

---

### Runtime Dependencies

#### 🧾 Serialization

| Dependency                                               | Scope   |
|----------------------------------------------------------|---------|
| `com.fasterxml.jackson.core:jackson-databind`            | runtime |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | runtime |
| `com.fasterxml.jackson.datatype:jackson-datatype-jdk8`   | runtime |
| `org.msgpack:msgpack-core`                               | runtime |
| `org.lz4:lz4-java`                                       | runtime |

#### 🔍 Payload Validation

| Dependency                                    | Scope   |
|-----------------------------------------------|---------|
| `org.hibernate.validator:hibernate-validator` | runtime |
| `org.glassfish.expressly:expressly`           | runtime |
| `org.jboss.logging:jboss-logging`             | runtime |

#### 🌐 Web and Transport

| Dependency                            | Scope   |
|---------------------------------------|---------|
| `io.undertow:undertow-websockets-jsr` | runtime |
| `io.jooby:jooby`                      | runtime |

#### ⚙️ Utilities

| Dependency                         | Scope   |
|------------------------------------|---------|
| `org.apache.commons:commons-lang3` | runtime |

---

### Optional Integrations

#### 🌱 Spring Support

| Dependency                           | Scope    |
|--------------------------------------|----------|
| `org.springframework:spring-context` | optional |
| `org.springframework:spring-test`    | test     |

#### 💻 Kotlin Support

| Dependency                                           | Scope    |
|------------------------------------------------------|----------|
| `com.fasterxml.jackson.module:jackson-module-kotlin` | optional |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8`            | optional |
| `org.jetbrains.kotlin:kotlin-reflect`                | optional |
| `org.jetbrains.kotlin:kotlin-test`                   | test     |

---

### Test Dependencies

| Dependency                                   | Scope |
|----------------------------------------------|-------|
| `org.junit.jupiter:junit-jupiter-engine`     | test  |
| `org.junit.platform:junit-platform-launcher` | test  |
| `org.mockito:mockito-core`                   | test  |
| `org.jooq:joor`                              | test  |
| `org.hamcrest:hamcrest-library`              | test  |
| `org.jboss.resteasy:resteasy-undertow`       | test  |

---

> ℹ️ Dependencies marked as `optional` are **not required** unless you use the corresponding integration.  
> For example, Spring or Kotlin support is automatically detected when available but safely ignored otherwise.

---

### Versioning Policy

Flux Capacitor aims to stay up-to-date with its core dependencies. We strive to:

- Use the **latest stable versions** where possible,
- Avoid breaking backward compatibility for common transitive consumers,
- Update frequently, especially for frameworks like **Jackson**, **Spring**, **Hibernate Validator**, and **JUnit**.