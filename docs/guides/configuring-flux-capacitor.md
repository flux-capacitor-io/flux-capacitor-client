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