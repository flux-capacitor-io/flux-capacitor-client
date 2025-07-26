## Handling WebSocket Messages

Flux Capacitor provides first-class support for **WebSocket communication**, enabling stateful or stateless message
handing using the same annotation-based model as other requests.

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