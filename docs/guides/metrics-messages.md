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