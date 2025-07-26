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