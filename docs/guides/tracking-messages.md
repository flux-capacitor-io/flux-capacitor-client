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