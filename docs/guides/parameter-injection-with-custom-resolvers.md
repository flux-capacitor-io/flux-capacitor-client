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