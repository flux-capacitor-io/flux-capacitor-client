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