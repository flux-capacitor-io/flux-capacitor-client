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