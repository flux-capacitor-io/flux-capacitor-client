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