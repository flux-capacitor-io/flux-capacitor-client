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